package com.espoir.bumblebeecode.okws

import com.espoir.bumblebeecode.Bumblebee
import com.espoir.bumblebeecode.Bumblebee.Companion.TAG
import com.espoir.bumblebeecode.okws.request.RequestFactory
import okhttp3.OkHttpClient
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class OkConnectionEstablisher(
    private val okHttpClient: OkHttpClient,
    private val requestFactory: RequestFactory,
) : OkHttpWebSocket.ConnectionEstablisher {

    override fun establishConnection(webSocketListener: WebSocketListener): WebSocket {
        val request = requestFactory.createRequest()
        val ws = okHttpClient.newWebSocket(request, webSocketListener)
        Bumblebee.log.log(TAG, "Socket状态： 创建新的 Socket = $ws")
        return ws
    }
}