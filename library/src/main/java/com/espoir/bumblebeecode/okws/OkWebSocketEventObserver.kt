package com.espoir.bumblebeecode.okws

import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.ShutdownReason
import com.espoir.bumblebeecode.code.WebSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.ByteString

class OkWebSocketEventObserver(private val scope: CoroutineScope) : WebSocketListener() {

    private val processor: MutableSharedFlow<WebSocket.Event> by lazy {
        MutableSharedFlow()
    }

    fun observe(): Flow<WebSocket.Event> = processor.buffer()

    fun terminate() {
        scope.launch { processor.emit(WebSocket.Event.OnTerminate) }
    }

    override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
        scope.launch { processor.emit(WebSocket.Event.OnConnectionOpened(webSocket)) }
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
        scope.launch { processor.emit(WebSocket.Event.OnMessageReceived(Message.Bytes(bytes.toByteArray()))) }
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        if (text == "ping") return
        scope.launch { processor.emit(WebSocket.Event.OnMessageReceived(Message.Text(text))) }
    }

    override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        scope.launch { processor.emit(WebSocket.Event.OnConnectionClosing(ShutdownReason(code, reason))) }
    }

    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        scope.launch { processor.emit(WebSocket.Event.OnConnectionClosed(ShutdownReason(code, reason))) }
    }

    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
        scope.launch { processor.emit(WebSocket.Event.OnConnectionFailed(t)) }
    }
}