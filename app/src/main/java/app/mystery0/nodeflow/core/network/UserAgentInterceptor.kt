package app.mystery0.nodeflow.core.network

import okhttp3.Interceptor
import okhttp3.Response

class UserAgentInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val requestWithUserAgent = if (request.header(USER_AGENT_HEADER).isNullOrBlank()) {
            request.newBuilder()
                .header(USER_AGENT_HEADER, USER_AGENT)
                .build()
        } else {
            request
        }
        return chain.proceed(requestWithUserAgent)
    }

    private companion object {
        const val USER_AGENT_HEADER = "User-Agent"
        const val USER_AGENT = V2exUserAgents.MOBILE
    }
}
