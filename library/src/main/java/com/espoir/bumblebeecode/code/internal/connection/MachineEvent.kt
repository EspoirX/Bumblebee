package com.espoir.bumblebeecode.code.internal.connection

import com.espoir.bumblebeecode.code.WebSocket


/**
 * 状态机事件
 */
sealed class MachineEvent {

    /**
     * 链接 事件
     */
    sealed class OnConnectionEvent : MachineEvent() {
        /**
         * app 打开，并登录成功
         */
        object StartedConnection : OnConnectionEvent()

        /**
         * app 关闭 或者退出登录
         */
        object TerminateConnection : OnConnectionEvent()
    }

    /**
     * socket 事件
     */
    sealed class OnWebSocket : MachineEvent() {
        data class Event<out T : WebSocket.Event> internal constructor(val event: T) : OnWebSocket()
    }

    /**
     * 状态改变
     */
    data class OnStateChange<out T : MachineState> internal constructor(val state: T) : MachineEvent()

    /**
     * 重试事件
     */
    object OnRetry : MachineEvent()
}