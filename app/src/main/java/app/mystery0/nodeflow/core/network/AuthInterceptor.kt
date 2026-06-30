package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.datastore.SessionStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionStore: SessionStore,
    private val cookieJar: V2exCookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = runBlocking { sessionStore.session.first() }
        val request = chain.request()
        session.cookieHeader
            ?.takeIf { it.isNotBlank() && cookieJar.cookieHeader(request.url).isBlank() }
            ?.let { cookieJar.restoreFromCookieHeader(request.url, it) }
        val requestBuilder = chain.request().newBuilder()
        session.personalAccessToken
            ?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("Authorization", "Bearer $it") }
        return chain.proceed(requestBuilder.build())
    }
}
