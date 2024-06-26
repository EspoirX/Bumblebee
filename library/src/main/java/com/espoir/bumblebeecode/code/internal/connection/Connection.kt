package com.espoir.bumblebeecode.code.internal.connection

import android.os.Handler
import android.os.Looper
import com.espoir.bumblebeecode.Bumblebee.Companion.TAG
import com.espoir.bumblebeecode.Bumblebee.Companion.log
import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.ShutdownReason
import com.espoir.bumblebeecode.code.StateMachine
import com.espoir.bumblebeecode.code.StateMachine.Matcher.Companion.any
import com.espoir.bumblebeecode.code.WebSocket
import com.espoir.bumblebeecode.code.retry.BackoffStrategy
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class Connection(private val stateManager: StateManager) {

    class Factory(
        private val webSocketFactory: WebSocket.Factory,
        private val backoffStrategy: BackoffStrategy,
    ) {

        fun create(): Connection {
            val stateManager = StateManager(webSocketFactory, backoffStrategy)
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

    fun observeEvent(tag: String): Flow<MachineEvent> = stateManager.observeMachineEvent(tag)

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
        private val webSocketFactory: WebSocket.Factory,
        private val backoffStrategy: BackoffStrategy,
    ) {

        val state: MachineState
            get() = stateMachine.state

        private val stateMachine = StateMachine.create<MachineState, MachineEvent, SideEffect> {
            //一开始是没链接
            state<MachineState.Disconnected> {
                onEnter {
                    if (openConnect) {
                        //状态转为 StartedConnection
                        log.log(TAG, "Socket状态： 已断开 -> 打开链接")
                        handleEvent(MachineEvent.OnConnectionEvent.StartedConnection)
                    } else {
                        log.log(TAG, "Socket状态： 已断开")
                    }
                }
                //状态为 StartedConnection 时，打开 socket 链接，转为 Connecting
                on(MachineEvent.OnConnectionEvent.StartedConnection) {
                    val webSocketSession = createWebSocket()
                    log.log(TAG, "Socket状态： 打开链接 -> 正在链接")
                    transitionTo(MachineState.Connecting(session = webSocketSession, retryCount = 0))
                }
            }
            //socket正在链接
            state<MachineState.Connecting> {
                onEnter {
                    //链接中的时候才真正打开 socket，避免 socket 回调回来的时候，状态机状态还没改变的时序问题
                    openWebSocket(session.webSocket)
                }
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
                        log.log(TAG, "Socket状态： 正在链接 -> 链接失败，转重连，次数=$retryCount")
                        val backoffDuration = backoffStrategy.backoffDurationMillisAt(retryCount)
                        if (backoffDuration == -1L) {
                            log.log(TAG, "Socket状态： 重连取消，转断开")
                            session.webSocket.close(ShutdownReason.ACTIVELY)
                            transitionTo(MachineState.Disconnected(false))
                        } else {
                            scheduleRetry(backoffDuration)
                            transitionTo(
                                MachineState.WaitingToRetry(
                                    session,
                                    retryCount = retryCount,
                                    retryInMillis = backoffDuration
                                )
                            )
                        }
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
                        if (backoffDuration == -1L) {
                            log.log(TAG, "Socket状态： 重连取消，转断开")
                            session.webSocket.close(ShutdownReason.ACTIVELY)
                            transitionTo(MachineState.Disconnected(false))
                        } else {
                            scheduleRetry(backoffDuration)
                            transitionTo(
                                MachineState.WaitingToRetry(
                                    session,
                                    retryCount = 0,
                                    retryInMillis = backoffDuration
                                )
                            )
                        }
                    }
                }
                on(webSocketClosed()) {
                    if (isRetrying) {
                        dontTransition()
                    } else {
                        log.log(TAG, "Socket状态： 链接成功 -> 已经关闭，转重连")
                        val backoffDuration = backoffStrategy.backoffDurationMillisAt(0)
                        if (backoffDuration == -1L) {
                            log.log(TAG, "Socket状态： 重连取消，转断开")
                            session.webSocket.close(ShutdownReason.ACTIVELY)
                            transitionTo(MachineState.Disconnected(false))
                        } else {
                            scheduleRetry(backoffDuration)
                            transitionTo(
                                MachineState.WaitingToRetry(
                                    session,
                                    retryCount = 0,
                                    retryInMillis = backoffDuration
                                )
                            )
                        }
                    }
                }

                on(webSocketTerminate()) {
                    if (isRetrying) {
                        dontTransition()
                    } else {
                        log.log(TAG, "Socket状态： 链接成功 -> 链接终止，转重连")
                        val backoffDuration = backoffStrategy.backoffDurationMillisAt(0)
                        if (backoffDuration == -1L) {
                            log.log(TAG, "Socket状态： 重连取消，转断开")
                            session.webSocket.close(ShutdownReason.ACTIVELY)
                            transitionTo(MachineState.Disconnected(false))
                        } else {
                            scheduleRetry(backoffDuration)
                            transitionTo(
                                MachineState.WaitingToRetry(
                                    session,
                                    retryCount = 0,
                                    retryInMillis = backoffDuration
                                )
                            )
                        }
                    }
                }

                on<MachineEvent.OnConnectionEvent.TerminateConnection> {
                    log.log(TAG, "Socket状态： 链接成功 -> 主动关闭，断开中")
                    session.webSocket.close(ShutdownReason.ACTIVELY)
                    transitionTo(MachineState.Disconnected(false))
                }
            }
            state<MachineState.WaitingToRetry> {
                //重试，重新构建websocket
                on<MachineEvent.OnRetry> {
                    session.webSocket.cancel()
                    log.log(TAG, "Socket状态： 重连中 先取消当前 socket")
                    val webSocketSession = createWebSocket()
                    transitionTo(MachineState.Connecting(session = webSocketSession, retryCount = retryCount + 1))
                }
                on<MachineEvent.OnConnectionEvent.TerminateConnection> {
                    session.webSocket.cancel()
                    log.log(TAG, "Socket状态： 重连中 -> 主动关闭，取消重连，取消 socket")
                    cancelRetry()
                    transitionTo(MachineState.Disconnected(false))
                }
            }

            initialState(MachineState.Disconnected())
            onTransition { transition ->
                if (transition is StateMachine.Transition.Valid && transition.fromState != transition.toState) {
                    log.log(TAG, "Socket状态变更 state = $state")
                    eventProcessor(MachineEvent.OnStateChange(state))
                }
            }
        }

        private var eventProcessorMap = hashMapOf<String, EventProcessor>()

        private fun addEventProcessor(tag: String, eventProcessor: EventProcessor) {
            eventProcessorMap[tag] = eventProcessor
        }

        private fun eventProcessor(event: MachineEvent) {
            eventProcessorMap.forEach { it.value.onEvent(event) }
        }

        fun observeMachineEvent(tag: String): Flow<MachineEvent> = callbackFlow {
            addEventProcessor(tag, object : EventProcessor {
                override fun onEvent(event: MachineEvent) {
                    trySend(event).isSuccess
                }
            })
            awaitClose()
        }

        interface EventProcessor {
            fun onEvent(event: MachineEvent)
        }

        /**
         * 改变状态
         */
        fun handleEvent(event: MachineEvent) {
            stateMachine.transition(event)
            eventProcessor(event)
        }

        private fun createWebSocket(): Session {
            val webSocket = webSocketFactory.create()
            return Session(webSocket)
        }

        private fun openWebSocket(webSocket: WebSocket) {
            webSocket.open {
                runCatching {
                    handleEvent(MachineEvent.OnWebSocket.Event(it))
                }.onFailure {
                    it.printStackTrace()
                    log.log(TAG, "OkHttpWebSocket catch = " + it.message)
                    handleEvent(MachineEvent.OnWebSocket.Event(WebSocket.Event.OnConnectionFailed(it)))
                }
            }
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


        private fun cancelRetry() = handler.removeCallbacksAndMessages(null)

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