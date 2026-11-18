package app.mystery0.nodeflow.core.network.ech

import android.net.ConnectivityManager
import android.os.SystemClock
import app.mystery0.nodeflow.core.datastore.SettingsStore
import app.mystery0.nodeflow.domain.settings.EchConnection
import app.mystery0.nodeflow.domain.settings.EchStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import java.net.Proxy

/** 单个来源的所有候选都失败后才切换来源。用户配置始终排在内置来源之后。 */
internal fun <T> findEchRoute(
    fallback: String,
    lookup: (String, String) -> List<EchCandidate>,
    verify: (EchCandidate) -> T?,
): Pair<T, Long>? {
    for (resolver in (builtInResolvers + fallback).filter(String::isNotBlank).distinct()) {
        for (host in listOf(V2EX_HOST, BOOTSTRAP_HOST)) {
            val candidates = try { lookup(resolver, host) } catch (_: Exception) { continue }
            for (candidate in candidates) {
                val route = try { verify(candidate) } catch (_: IOException) { null }
                if (route != null) return route to candidate.ttl
            }
        }
    }
    return null
}

internal class EchRouting(
    private val store: SettingsStore,
    private val connectivity: ConnectivityManager,
) : EchConnection, Interceptor {
    private val dns by lazy { EchDns() }
    private val tls by lazy { EchTls() }
    private val mutex = Mutex()
    private val state = MutableStateFlow(EchStatus.Idle)
    override val status = state.asStateFlow()
    private data class Cached(val network: Long?, val fallback: String, val until: Long, val client: OkHttpClient?)
    private var cached: Cached? = null

    private suspend fun route(force: Boolean): OkHttpClient? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val settings = store.settings.first()
            if (!settings.echEnabled) {
                cached = null
                state.value = EchStatus.Idle
                return@withLock null
            }
            val network = connectivity.activeNetwork?.networkHandle
            val fallback = settings.echFallbackDns
            cached?.takeIf { !force && it.network == network && it.fallback == fallback && it.until > SystemClock.elapsedRealtime() }?.let {
                return@withLock it.client ?: throw IOException("ECH connection unavailable")
            }
            state.value = EchStatus.Checking
            val result = try {
                findEchRoute(fallback, dns::lookup) { tls.verify(it) }
            } catch (_: java.security.GeneralSecurityException) { null }
              catch (_: LinkageError) { null }
            val client = result?.first
            cached = Cached(network, fallback, SystemClock.elapsedRealtime() + (result?.second?.coerceIn(0, 3600) ?: 5) * 1000, client)
            state.value = if (client != null) EchStatus.Ready else EchStatus.Unavailable
            client ?: throw IOException("ECH connection unavailable")
        }
    }

    override suspend fun check(force: Boolean) {
        try { route(force) } catch (_: IOException) { /* 失败状态已发布到设置页。 */ }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps || request.url.host != V2EX_HOST || request.url.port != 443) return chain.proceed(request)
        val route = runBlocking { route(false) } ?: return chain.proceed(request)
        // 仅覆盖连接参数，沿用认证拦截器、Cookie 和写请求的重试策略。
        return chain.withProxy(Proxy.NO_PROXY).withDns(route.dns)
            .withSslSocketFactory(route.sslSocketFactory, route.x509TrustManager)
            .withConnectionPool(route.connectionPool).proceed(request)
    }
}
