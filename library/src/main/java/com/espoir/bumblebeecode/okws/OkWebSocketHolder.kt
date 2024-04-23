package com.espoir.bumblebeecode.okws

import com.espoir.bumblebeecode.Bumblebee
import com.espoir.bumblebeecode.Bumblebee.Companion.TAG
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString

class OkWebSocketHolder : WebSocket {

    private var webSocket: WebSocket? = null

    fun initiate(webSocket: WebSocket) {
        this.webSocket = webSocket
        Bumblebee.log.log(TAG, "webSocket = $webSocket")
    }

    fun shutdown() {
        webSocket = null
    }

    override fun cancel() = webSocket?.cancel() ?: Unit

    override fun close(code: Int, reason: String?): Boolean = webSocket?.close(code, reason) ?: false

    override fun queueSize(): Long = throw UnsupportedOperationException()

    override fun request(): Request = throw UnsupportedOperationException()

    override fun send(text: String): Boolean = webSocket?.send(text) ?: false

    override fun send(bytes: ByteString): Boolean = webSocket?.send(bytes) ?: false
}