
package com.espoir.bumblebeecode.okws.request

import androidx.collection.ArrayMap
import okhttp3.Request

internal class StaticUrlRequestFactory(
    private val url: String,
    private val headers: ArrayMap<String, String>,
) : RequestFactory {

    override fun createRequest(): Request = Request.Builder()
        .url(url)
        .apply {
            headers.forEach { header(it.key, it.value) }
        }
        .build()
}
