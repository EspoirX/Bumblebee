
package com.espoir.bumblebeecode.okws.request

import okhttp3.Request

interface RequestFactory {
    fun createRequest(): Request
}
