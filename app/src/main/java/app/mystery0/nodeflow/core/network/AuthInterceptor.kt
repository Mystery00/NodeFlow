package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.datastore.SessionStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val session = runBlocking { sessionStore.session.first() }
        val requestBuilder = chain.request().newBuilder()
        session.personalAccessToken
            ?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("Authorization", "Bearer $it") }
        session.cookieHeader
            ?.takeIf { it.isNotBlank() }
            ?.let { requestBuilder.header("Cookie", it) }
        return chain.proceed(requestBuilder.build())
    }
}
