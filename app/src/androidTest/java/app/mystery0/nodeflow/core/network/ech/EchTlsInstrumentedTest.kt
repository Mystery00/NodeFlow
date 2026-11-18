package app.mystery0.nodeflow.core.network.ech

import com.google.common.truth.Truth.assertThat
import java.io.DataInputStream
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.Base64
import java.util.concurrent.Callable
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLSocket
import org.junit.Test

class EchTlsInstrumentedTest {
    @Test
    fun clientHelloExposesOnlyCoverName() {
        for (host in listOf(V2EX_HOST, "cdn.v2ex.com", "i.v2ex.co", "i.v2ex.com")) {
            assertCoverName(host)
        }
    }

    private fun assertCoverName(host: String) {
        // 合成配置中的外层名称为 cover.example，只在回环地址捕获握手首包。
        val config = Base64.getDecoder().decode(
            "AED+DQA8AQAgACAJAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEAAEAAQANY292ZXIuZXhhbXBsZQAA",
        )
        val factory = EchTls().socketFactory(host, config)
        val loopback = InetAddress.getLoopbackAddress()
        ServerSocket(0, 1, loopback).use { listener ->
            listener.soTimeout = 5000
            val firstRecord = FutureTask(Callable {
                listener.accept().use { peer ->
                    peer.soTimeout = 5000
                    val input = DataInputStream(peer.getInputStream())
                    assertThat(input.readUnsignedByte()).isEqualTo(22)
                    input.readUnsignedShort()
                    ByteArray(input.readUnsignedShort()).also(input::readFully)
                }
            })
            Thread(firstRecord).start()
            Socket(loopback, listener.localPort).use { tcp ->
                (factory.createSocket(tcp, host, 443, true) as SSLSocket).use { tls ->
                    tls.soTimeout = 5000
                    try {
                        tls.startHandshake()
                    } catch (_: IOException) {
                        // 捕获端读完 ClientHello 即关闭。
                    }
                }
            }
            val hello = firstRecord.get(6, TimeUnit.SECONDS).toString(Charsets.ISO_8859_1)
            assertThat(hello).contains("cover.example")
            assertThat(hello).doesNotContain(host)
        }
    }
}
