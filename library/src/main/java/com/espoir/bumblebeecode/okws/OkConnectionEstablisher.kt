package com.espoir.bumblebeecode.okws

import com.espoir.bumblebeecode.okws.request.RequestFactory
import okhttp3.OkHttpClient
import okhttp3.WebSocketListener

class OkConnectionEstablisher(
    private val okHttpClient: OkHttpClient,
    private val requestFactory: RequestFactory,
) : OkHttpWebSocket.ConnectionEstablisher {

    override fun establishConnection(webSocketListener: WebSocketListener) {
        val request = requestFactory.createRequest()
        okHttpClient.newWebSocket(request, webSocketListener)
    }
}