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

private val echImageHosts = setOf("cdn.v2ex.com", "i.v2ex.co", "i.v2ex.com")

internal fun usesEch(url: okhttp3.HttpUrl, images: Boolean): Boolean =
    url.isHttps && url.port == 443 && (url.host == V2EX_HOST || (images && url.host in echImageHosts))

/** 单个来源的所有候选都失败后才切换来源。用户配置始终排在内置来源之后。 */
internal fun <T> findEchRoute(
    fallback: String,
    lookup: (String, String) -> List<EchCandidate>,
    targetHost: String = V2EX_HOST,
    verify: (EchCandidate) -> T?,
): Pair<T, Long>? {
    for (resolver in (builtInResolvers + fallback).filter(String::isNotBlank).distinct()) {
        for (host in listOf(targetHost, BOOTSTRAP_HOST)) {
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
    // 独立的探测锁和缓存，列表中并发加载头像时只探测一次，不阻塞主站。
    private class HostRoute {
        val mutex = Mutex()
        var cached: Cached? = null
    }
    private val hosts = (echImageHosts + V2EX_HOST).associateWith { HostRoute() }
    private val state = MutableStateFlow(EchStatus.Idle)
    override val status = state.asStateFlow()
    private data class Cached(val network: Long?, val fallback: String, val until: Long, val client: OkHttpClient?)
    private fun publish(host: String, status: EchStatus) {
        if (host == V2EX_HOST) state.value = status
    }

    private suspend fun route(host: String, force: Boolean): OkHttpClient? = withContext(Dispatchers.IO) {
        val slot = hosts.getValue(host)
        slot.mutex.withLock {
            val settings = store.settings.first()
            if (!settings.echEnabled) {
                slot.cached = null
                publish(host, EchStatus.Idle)
                return@withLock null
            }
            val network = connectivity.activeNetwork?.networkHandle
            val fallback = settings.echFallbackDns
            slot.cached?.takeIf { !force && it.network == network && it.fallback == fallback && it.until > SystemClock.elapsedRealtime() }?.let {
                return@withLock it.client ?: throw IOException("ECH connection unavailable")
            }
            publish(host, EchStatus.Checking)
            val result = try {
                findEchRoute(fallback, dns::lookup, host) { tls.verify(host, it) }
            } catch (_: java.security.GeneralSecurityException) { null }
              catch (_: LinkageError) { null }
            val client = result?.first
            slot.cached = Cached(network, fallback, SystemClock.elapsedRealtime() + (result?.second?.coerceIn(0, 3600) ?: 5) * 1000, client)
            publish(host, if (client != null) EchStatus.Ready else EchStatus.Unavailable)
            client ?: throw IOException("ECH connection unavailable")
        }
    }

    override suspend fun check(force: Boolean) {
        try { route(V2EX_HOST, force) } catch (_: IOException) { /* 失败状态已发布到设置页。 */ }
    }

    override fun intercept(chain: Interceptor.Chain): Response = intercept(chain, images = false)

    val imageInterceptor = Interceptor { intercept(it, images = true) }

    private fun intercept(chain: Interceptor.Chain, images: Boolean): Response {
        val request = chain.request()
        if (!usesEch(request.url, images)) return chain.proceed(request)
        val route = runBlocking { route(request.url.host, false) } ?: return chain.proceed(request)
        // 仅覆盖连接参数，沿用认证拦截器、Cookie 和写请求的重试策略。
        return chain.withProxy(Proxy.NO_PROXY).withDns(route.dns)
            .withSslSocketFactory(route.sslSocketFactory, route.x509TrustManager)
            .withConnectionPool(route.connectionPool).proceed(request)
    }
}
