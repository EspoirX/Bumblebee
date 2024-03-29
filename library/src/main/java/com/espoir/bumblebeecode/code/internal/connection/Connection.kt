package com.espoir.bumblebeecode.code.internal.connection

import android.os.Handler
import android.os.Looper
import com.espoir.bumblebeecode.Bumblebee.Companion.TAG
import com.espoir.bumblebeecode.BumblebeeLog
import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.ShutdownReason
import com.espoir.bumblebeecode.code.StateMachine
import com.espoir.bumblebeecode.code.StateMachine.Matcher.Companion.any
import com.espoir.bumblebeecode.code.WebSocket
import com.espoir.bumblebeecode.code.retry.BackoffStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class Connection(private val stateManager: StateManager) {

    class Factory(
        private val log: BumblebeeLog,
        private val webSocketFactory: WebSocket.Factory,
        private val backoffStrategy: BackoffStrategy,
    ) {

        fun create(scope: CoroutineScope): Connection {
            val stateManager = StateManager(scope, webSocketFactory, backoffStrategy, log)
            return Connection(stateManager)
        }
    }


    fun startForever() {
        if (stateManager.state is MachineState.Disconnected) {
            stateManager.handleEvent(MachineEvent.OnConnectionEvent.StartedConnection)
        }
    }

    fun shutdown() {
        stateManager.handleEvent(MachineEvent.OnConnectionEvent.TerminateConnection)
    }

    fun observeEvent(): Flow<MachineEvent> = stateManager.observeMachineEvent()

    fun send(message: Message): Boolean {
        return when (val state = stateManager.state) {
            is MachineState.Connected -> state.session.webSocket.send(message)
            else -> false
        }
    }

    fun isConnectOpen(): Boolean = stateManager.state is MachineState.Connected

    /**
     * 状态管理，基于有限状态机
     */
    class StateManager(
        private val scope: CoroutineScope,
        private val webSocketFactory: WebSocket.Factory,
        private val backoffStrategy: BackoffStrategy,
        private val log: BumblebeeLog,
    ) {

        private val eventProcessor: MutableSharedFlow<MachineEvent> by lazy {
            MutableSharedFlow()
        }

        val state: MachineState
            get() = stateMachine.state

        private val stateMachine = StateMachine.create<MachineState, MachineEvent, SideEffect> {
            //一开始是没链接
            state<MachineState.Disconnected> {
                onEnter {
                    //状态转为 StartedConnection
                    log.log(TAG, "Socket状态： 已断开 -> 打开链接")
                    handleEvent(MachineEvent.OnConnectionEvent.StartedConnection)
                }
                //状态为 StartedConnection 时，打开 socket 链接，转为 Connecting
                on(MachineEvent.OnConnectionEvent.StartedConnection) {
                    val webSocketSession = openWebSocket()
                    log.log(TAG, "Socket状态： 打开链接 -> 正在链接")
                    transitionTo(MachineState.Connecting(session = webSocketSession, retryCount = 0))
                }
            }
            //socket正在链接
            state<MachineState.Connecting> {
                //已经链接成功，转到 Connected
                on(webSocketOpen()) {
                    log.log(TAG, "Socket状态： 正在链接 -> 链接成功")
                    transitionTo(MachineState.Connected(session = session))
                }
                //链接失败，重试
                on(webSocketFailed()) {
                    if (isRetrying) {
                        dontTransition()
                    } else {
                        log.log(TAG, "Socket状态： 正在链接 -> 链接失败，转重连")
                        val backoffDuration = backoffStrategy.backoffDurationMillisAt(retryCount)
                        scheduleRetry(backoffDuration)
                        transitionTo(
                            MachineState.WaitingToRetry(
                                retryCount = retryCount,
                                retryInMillis = backoffDuration
                            )
                        )
                    }
                }
            }
            state<MachineState.Connected> {
                on(webSocketClosing()) {
                    if (isRetrying) {
                        dontTransition()
                    } else {
                        log.log(TAG, "Socket状态： 链接成功 -> 正在关闭，转重连")
                        val backoffDuration = backoffStrategy.backoffDurationMillisAt(0)
                        scheduleRetry(backoffDuration)
                        transitionTo(
                            MachineState.WaitingToRetry(
                                retryCount = 0,
                                retryInMillis = backoffDuration
                            )
                        )
                    }
                }
                on(webSocketClosed()) {
                    if (isRetrying) {
                        dontTransition()
                    } else {
                        log.log(TAG, "Socket状态： 链接成功 -> 已经关闭，转重连")
                        val backoffDuration = backoffStrategy.backoffDurationMillisAt(0)
                        scheduleRetry(backoffDuration)
                        transitionTo(
                            MachineState.WaitingToRetry(
                                retryCount = 0,
                                retryInMillis = backoffDuration
                            )
                        )
                    }
                }

                on(webSocketTerminate()) {
                    if (isRetrying) {
                        dontTransition()
                    } else {
                        log.log(TAG, "Socket状态： 链接成功 -> 链接终止，转重连")
                        val backoffDuration = backoffStrategy.backoffDurationMillisAt(0)
                        scheduleRetry(backoffDuration)
                        transitionTo(
                            MachineState.WaitingToRetry(
                                retryCount = 0,
                                retryInMillis = backoffDuration
                            )
                        )
                    }
                }

                on<MachineEvent.OnConnectionEvent.TerminateConnection> {
                    log.log(TAG, "Socket状态： 链接成功 -> 主动关闭，断开中")
                    session.webSocket.close(ShutdownReason.ACTIVELY)
                    transitionTo(MachineState.Disconnecting)
                }
            }
            state<MachineState.Disconnecting> {
                onEnter {
                    log.log(TAG, "Socket状态： 断开中 -> 已经断开")
                    transitionTo(MachineState.Disconnected)
                }
            }
            state<MachineState.WaitingToRetry> {
                //重试，重新构建websocket
                on<MachineEvent.OnRetry> {
                    log.log(TAG, "Socket状态： 重连中")
                    val webSocketSession = openWebSocket()
                    transitionTo(MachineState.Connecting(session = webSocketSession, retryCount = retryCount + 1))
                }
                on<MachineEvent.OnConnectionEvent.TerminateConnection> {
                    log.log(TAG, "Socket状态： 重连中 -> 主动关闭，取消重连")
                    cancelRetry()
                    transitionTo(MachineState.Disconnected)
                }
            }

            initialState(MachineState.Disconnected)
            onTransition { transition ->
                if (transition is StateMachine.Transition.Valid && transition.fromState != transition.toState) {
                    scope.launch { eventProcessor.emit(MachineEvent.OnStateChange(state)) }
                }
            }
        }

        fun observeMachineEvent(): Flow<MachineEvent> = eventProcessor.buffer()

        /**
         * 改变状态
         */
        fun handleEvent(event: MachineEvent) {
            scope.launch { eventProcessor.emit(event) }
            stateMachine.transition(event)
        }

        private fun openWebSocket(): Session {
            val webSocket = webSocketFactory.create(scope)
            webSocket.open().map {
                handleEvent(MachineEvent.OnWebSocket.Event(it))
            }.launchIn(scope)
            return Session(webSocket)
        }

        @Volatile
        private var isRetrying = false
        private val handler = Handler(Looper.getMainLooper())

        private fun scheduleRetry(duration: Long) {
            isRetrying = true
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({
                handleEvent(MachineEvent.OnRetry)
                isRetrying = false
            }, duration)
        }

        private fun MachineState.WaitingToRetry.cancelRetry() = handler.removeCallbacksAndMessages(null)

        private fun webSocketOpen() = any<MachineEvent, MachineEvent.OnWebSocket.Event<*>>()
            .where { event is WebSocket.Event.OnConnectionOpened<*> }

        private fun webSocketFailed() = any<MachineEvent, MachineEvent.OnWebSocket.Event<*>>()
            .where { event is WebSocket.Event.OnConnectionFailed }

        private fun webSocketClosing() = any<MachineEvent, MachineEvent.OnWebSocket.Event<*>>()
            .where { event is WebSocket.Event.OnConnectionClosing }

        private fun webSocketClosed() = any<MachineEvent, MachineEvent.OnWebSocket.Event<*>>()
            .where { event is WebSocket.Event.OnConnectionClosed }

        private fun webSocketTerminate() = any<MachineEvent, MachineEvent.OnWebSocket.Event<*>>()
            .where { event is WebSocket.Event.OnTerminate }
    }
}