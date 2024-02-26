package com.espoir.bumblebeecode.okws

import androidx.collection.ArrayMap
import com.espoir.bumblebeecode.code.WebSocket
import com.espoir.bumblebeecode.okws.request.RequestFactory
import com.espoir.bumblebeecode.okws.request.StaticUrlRequestFactory
import okhttp3.OkHttpClient

fun OkHttpClient.newWebSocketFactory(url: String, headers: ArrayMap<String, String>): WebSocket.Factory {
    return newWebSocketFactory(StaticUrlRequestFactory(url, headers))
}

fun OkHttpClient.newWebSocketFactory(requestFactory: RequestFactory): WebSocket.Factory {
    return OkHttpWebSocket.Factory(OkConnectionEstablisher(this, requestFactory))
}