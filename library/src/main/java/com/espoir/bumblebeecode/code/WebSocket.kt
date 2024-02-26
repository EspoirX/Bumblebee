package com.espoir.bumblebeecode.code

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface WebSocket {

    fun open(): Flow<Event>
    fun send(message: Message): Boolean
    fun close(shutdownReason: ShutdownReason): Boolean
    fun cancel()

    sealed class Event {
        data class OnConnectionOpened<out WEB_SOCKET : Any>(val webSocket: WEB_SOCKET) : Event()
        data class OnMessageReceived(val message: Message) : Event()
        data class OnConnectionClosing(val shutdownReason: ShutdownReason) : Event()
        data class OnConnectionClosed(val shutdownReason: ShutdownReason) : Event()
        data class OnConnectionFailed(val throwable: Throwable) : Event()
        object OnTerminate : Event()
    }

    interface Factory {
        fun create(scope: CoroutineScope): WebSocket
    }
}