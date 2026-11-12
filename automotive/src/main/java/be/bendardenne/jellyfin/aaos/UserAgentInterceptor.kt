package be.bendardenne.jellyfin.aaos

import okhttp3.Interceptor
import okhttp3.Response

// Injects our UserAgent in http requests for easier server side logging
object UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("User-Agent", SharkMarmaladeConstants.USER_AGENT)
            .build()
        return chain.proceed(request)
    }
}
