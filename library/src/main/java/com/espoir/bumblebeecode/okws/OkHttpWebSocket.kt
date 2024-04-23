package com.espoir.bumblebeecode.okws

import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.ShutdownReason
import com.espoir.bumblebeecode.code.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import okhttp3.WebSocketListener
import okio.ByteString.Companion.toByteString

class OkHttpWebSocket(
    private val socketHolder: OkWebSocketHolder,
    private val eventObserver: OkWebSocketEventObserver,
    private val establisher: ConnectionEstablisher,
) : WebSocket {

    override fun open(): Flow<WebSocket.Event> {
        return eventObserver.observe()
            .onStart {
                establisher.establishConnection(eventObserver)
            }
            .map {
                handleWebSocketEvent(it)
                return@map it
            }.catch { it.printStackTrace() }
    }

    @Synchronized
    override fun send(message: Message): Boolean = when (message) {
        is Message.Text -> socketHolder.send(message.value)
        is Message.Bytes -> {
            val bytes = message.value
            val byteString = bytes.toByteString(0, bytes.size)
            socketHolder.send(byteString)
        }
    }

    @Synchronized
    override fun close(shutdownReason: ShutdownReason): Boolean {
        val (code, reasonText) = shutdownReason
        return socketHolder.close(code, reasonText)
    }

    @Synchronized
    override fun cancel() {
        socketHolder.cancel()
    }

    private fun handleWebSocketEvent(event: WebSocket.Event) {
        when (event) {
            is WebSocket.Event.OnConnectionOpened<*> -> {
                socketHolder.initiate(event.webSocket as okhttp3.WebSocket)
            }

            is WebSocket.Event.OnConnectionClosing -> close(ShutdownReason.DEFAULT)
            is WebSocket.Event.OnConnectionClosed,
            is WebSocket.Event.OnConnectionFailed,
            -> {
                handleConnectionShutdown()
            }

            else -> {}
        }
    }

    @Synchronized
    private fun handleConnectionShutdown() {
        socketHolder.shutdown()
        eventObserver.terminate()
    }

    interface ConnectionEstablisher {
        fun establishConnection(webSocketListener: WebSocketListener)
    }

    class Factory(
        private val establisher: ConnectionEstablisher,
    ) : WebSocket.Factory {
        override fun create(scope: CoroutineScope): WebSocket {
            return OkHttpWebSocket(OkWebSocketHolder(), OkWebSocketEventObserver(scope), establisher)
        }
    }
}