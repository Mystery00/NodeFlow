package app.mystery0.nodeflow.core.network.ech

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.conscrypt.Conscrypt
import org.conscrypt.ConscryptNetworkSecurityPolicy
import org.conscrypt.DomainEncryptionMode
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.Socket
import java.net.UnknownHostException
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import javax.net.ssl.X509ExtendedTrustManager

/**
 * EngineSocket 会给 X509ExtendedTrustManager 再套一层代理，导致它读不到 ECH 策略。
 * 使用普通接口保留策略对象；Conscrypt 的 Android 适配器通过反射调用带 Socket/Engine
 * 的校验方法，将握手上下文一并交给系统信任管理器。
 */
internal class EchTrustManager(private val system: X509ExtendedTrustManager) : X509TrustManager by system {
    @androidx.annotation.Keep
    fun getNetworkSecurityPolicy(): org.conscrypt.NetworkSecurityPolicy = object : ConscryptNetworkSecurityPolicy() {
        override fun getDomainEncryptionMode(hostname: String?) = DomainEncryptionMode.REQUIRED
    }

    @androidx.annotation.Keep
    fun checkClientTrusted(c: Array<X509Certificate>, a: String, s: Socket) = system.checkClientTrusted(c, a, s)
    @androidx.annotation.Keep
    fun checkServerTrusted(c: Array<X509Certificate>, a: String, s: Socket) = system.checkServerTrusted(c, a, s)
    @androidx.annotation.Keep
    fun checkClientTrusted(c: Array<X509Certificate>, a: String, e: SSLEngine) = system.checkClientTrusted(c, a, e)
    @androidx.annotation.Keep
    fun checkServerTrusted(c: Array<X509Certificate>, a: String, e: SSLEngine) = system.checkServerTrusted(c, a, e)
}

internal class EchSocketFactory(private val delegate: SSLSocketFactory, private val config: ByteArray) : SSLSocketFactory() {
    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        if (host != V2EX_HOST || port != 443) throw IOException("Unexpected ECH origin")
        val tls = delegate.createSocket(socket, host, port, autoClose) as SSLSocket
        Conscrypt.setHostname(tls, host)
        Conscrypt.setEchConfigList(tls, config)
        return tls
    }

    override fun getDefaultCipherSuites() = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites() = delegate.supportedCipherSuites
    override fun createSocket(host: String, port: Int): Socket = unsupported()
    override fun createSocket(host: String, port: Int, local: InetAddress, localPort: Int): Socket = unsupported()
    override fun createSocket(host: InetAddress, port: Int): Socket = unsupported()
    override fun createSocket(host: InetAddress, port: Int, local: InetAddress, localPort: Int): Socket = unsupported()
    private fun unsupported(): Nothing = throw IOException("ECH requires an authenticated hostname")
}

internal class EchTls {
    private val manager = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).run {
        init(null as KeyStore?)
        trustManagers.filterIsInstance<X509ExtendedTrustManager>().single()
    }
    private val context = SSLContext.getInstance("TLS", Conscrypt.newProvider()).apply {
        init(null, arrayOf(EchTrustManager(manager)), null)
    }

    fun socketFactory(config: ByteArray): SSLSocketFactory = EchSocketFactory(context.socketFactory, config)

    fun verify(candidate: EchCandidate): OkHttpClient? {
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY)
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    if (hostname != V2EX_HOST) throw UnknownHostException("Unexpected ECH origin")
                    return listOf(candidate.address)
                }
            }).sslSocketFactory(socketFactory(candidate.config), manager)
            .retryOnConnectionFailure(false).followRedirects(false).callTimeout(3, TimeUnit.SECONDS).build()
        val request = Request.Builder().url("https://$V2EX_HOST/cdn-cgi/trace").build()
        // 探测客户端没有账号拦截器或 Cookie。只接受目标站点确认加密的响应。
        return client.newCall(request).execute().use { response ->
            val source = response.body.source()
            if (!response.isSuccessful || source.request(8193)) return@use null
            val fields = source.readUtf8().lineSequence().toSet()
            client.takeIf { "sni=encrypted" in fields && "h=$V2EX_HOST" in fields }
        }
    }
}
