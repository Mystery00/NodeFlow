package app.mystery0.nodeflow.core.network.ech

import android.net.DnsResolver
import android.net.InetAddresses
import android.os.CancellationSignal
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.xbill.DNS.ARecord
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.HTTPSRecord
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Record
import org.xbill.DNS.SVCBBase
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

internal const val V2EX_HOST = "www.v2ex.com"
internal const val BOOTSTRAP_HOST = "cloudflare-ech.com"
internal const val SYSTEM_DNS = "system"
internal val builtInResolvers = listOf(SYSTEM_DNS, "https://dns.alidns.com/dns-query", "https://doh.pub/dns-query")

internal data class EchCandidate(val config: ByteArray, val address: InetAddress, val ttl: Long)

internal fun validResolver(server: String): Boolean = server.isEmpty() ||
    InetAddresses.isNumericAddress(server) || server.toHttpUrlOrNull()?.let {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.fragment == null
    } == true

internal class EchDns {
    private val http = OkHttpClient.Builder().callTimeout(3, TimeUnit.SECONDS)
        .followRedirects(false).retryOnConnectionFailure(false).build()

    fun lookup(server: String, host: String): List<EchCandidate> =
        resolveCandidates(host) { name, type -> records(server, name, type) }

    private fun records(server: String, name: Name, type: Int): List<Record> {
        val question = Record.newRecord(name, type, DClass.IN)
        val query = Message.newQuery(question)
        val wire = query.toWire()
        val bytes = when {
            server == SYSTEM_DNS -> systemQuery(wire)
            InetAddresses.isNumericAddress(server) -> udpQuery(InetAddresses.parseNumericAddress(server), wire)
            else -> dohQuery(server, wire)
        }
        val reply = Message(bytes)
        // Android 的系统解析器会重写事务 ID，其他来源需匹配请求 ID。
        if ((server != SYSTEM_DNS && reply.header.id != query.header.id) || reply.question != question ||
            !reply.header.getFlag(Flags.QR.toInt()) || reply.header.getFlag(Flags.TC.toInt()) || reply.rcode != Rcode.NOERROR
        ) return emptyList()
        return reply.getSection(Section.ANSWER).filter { it.name == name && it.type == type }
    }

    private fun systemQuery(wire: ByteArray): ByteArray {
        val result = CompletableFuture<ByteArray>()
        val cancel = CancellationSignal()
        return try {
            DnsResolver.getInstance().rawQuery(null, wire, DnsResolver.FLAG_EMPTY, Runnable::run, cancel,
                object : DnsResolver.Callback<ByteArray> {
                    override fun onAnswer(answer: ByteArray, rcode: Int) { result.complete(answer) }
                    override fun onError(error: DnsResolver.DnsException) { result.completeExceptionally(error) }
                })
            result.get(3, TimeUnit.SECONDS)
        } finally {
            cancel.cancel()
        }
    }

    private fun udpQuery(address: InetAddress, wire: ByteArray): ByteArray = DatagramSocket().use { socket ->
        socket.soTimeout = 3000
        socket.connect(address, 53)
        socket.send(DatagramPacket(wire, wire.size))
        val packet = DatagramPacket(ByteArray(65535), 65535)
        socket.receive(packet)
        packet.data.copyOf(packet.length)
    }

    private fun dohQuery(server: String, wire: ByteArray): ByteArray {
        val format = "application/dns-message"
        val request = Request.Builder().url(server).header("Accept", format)
            .post(wire.toRequestBody(format.toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("DNS query failed")
            val source = response.body.source()
            if (source.request(65536)) throw IOException("DNS message too large")
            source.readByteArray()
        }
    }
}

/** 地址提示为可选字段。缺少提示时向同一个 DNS 来源查询服务目标的地址。 */
internal fun resolveCandidates(host: String, records: (Name, Int) -> List<Record>): List<EchCandidate> {
    val name = Name.fromString("$host.")
    return records(name, Type.HTTPS).filterIsInstance<HTTPSRecord>()
        .filter { it.svcPriority > 0 }.sortedBy { it.svcPriority }.take(4).flatMap { rr ->
            val config = (rr.getSvcParamValue(SVCBBase.ECH) as? SVCBBase.ParameterEch)?.data
                ?.takeIf { it.isNotEmpty() } ?: return@flatMap emptyList()
            val v4 = (rr.getSvcParamValue(SVCBBase.IPV4HINT) as? SVCBBase.ParameterIpv4Hint)?.addresses.orEmpty()
            val v6 = (rr.getSvcParamValue(SVCBBase.IPV6HINT) as? SVCBBase.ParameterIpv6Hint)?.addresses.orEmpty()
            val hints = (v4 + v6).map { EchCandidate(config, it, rr.ttl) }
            hints.ifEmpty {
                val target = rr.targetName.takeUnless { it == Name.root } ?: name
                listOf(Type.A, Type.AAAA).flatMap { type ->
                    val answers = try { records(target, type) } catch (_: IOException) { emptyList() }
                    answers.mapNotNull { address ->
                        val ip = when (address) {
                            is ARecord -> address.address
                            is AAAARecord -> address.address
                            else -> return@mapNotNull null
                        }
                        EchCandidate(config, ip, minOf(rr.ttl, address.ttl))
                    }
                }
            }
        }.filterNot { candidate ->
            with(candidate.address) { isAnyLocalAddress || isLoopbackAddress || isLinkLocalAddress || isMulticastAddress || isSiteLocalAddress }
        }.take(4)
}
