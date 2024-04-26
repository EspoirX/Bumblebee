package com.espoir.bumblebeecode.okws

import com.espoir.bumblebeecode.Bumblebee.Companion.TAG
import com.espoir.bumblebeecode.Bumblebee.Companion.log
import com.espoir.bumblebeecode.code.Message
import com.espoir.bumblebeecode.code.ShutdownReason
import com.espoir.bumblebeecode.code.WebSocket
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.ByteString

class OkWebSocketEventObserver : WebSocketListener() {

    var callback: ((WebSocket.Event) -> Unit)? = null

    fun terminate() {
        log.log(TAG, "--Event OnTerminate--")
        callback?.invoke(WebSocket.Event.OnTerminate)
    }

    override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
        log.log(TAG, "OKHttp WebSocket onOpen webSocket = $webSocket")
        callback?.invoke(WebSocket.Event.OnConnectionOpened(webSocket))
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
        callback?.invoke(WebSocket.Event.OnMessageReceived(Message.Bytes(bytes.toByteArray())))
    }

    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
        if (text == "ping") return
        callback?.invoke(WebSocket.Event.OnMessageReceived(Message.Text(text)))
    }

    override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        log.log(TAG, "OKHttp  WebSocket onClosing reason=$reason")
        callback?.invoke(WebSocket.Event.OnConnectionClosing(ShutdownReason(code, reason)))
    }

    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
        log.log(TAG, "OKHttp  WebSocket onClosed reason=$reason")
        callback?.invoke(WebSocket.Event.OnConnectionClosed(ShutdownReason(code, reason)))
    }

    override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
        log.log(TAG, "OKHttp  WebSocket onFailure t = " + t.message)
        callback?.invoke(WebSocket.Event.OnConnectionFailed(t))
    }
}