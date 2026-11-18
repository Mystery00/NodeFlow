package app.mystery0.nodeflow.core.network.ech

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.net.InetAddress
import org.junit.Test

class EchRoutingTest {
    private val candidate = EchCandidate(byteArrayOf(1), InetAddress.getLoopbackAddress(), 60)

    @Test
    fun firstSuccessfulBuiltInStopsBeforeCustomDns() {
        val queries = mutableListOf<Pair<String, String>>()
        val result = findEchRoute("192.0.2.1", { server, host ->
            queries += server to host
            if (host == BOOTSTRAP_HOST) listOf(candidate) else emptyList()
        }) { "verified" }
        assertThat(queries).containsExactly(SYSTEM_DNS to V2EX_HOST, SYSTEM_DNS to BOOTSTRAP_HOST).inOrder()
        assertThat(result).isEqualTo("verified" to 60L)
    }

    @Test
    fun failedHandshakesAndDnsErrorsAdvanceToCustomDnsLast() {
        val queries = mutableListOf<String>()
        val result = findEchRoute("192.0.2.1", { server, host ->
            queries += server
            if (server == SYSTEM_DNS) throw IOException("offline")
            listOf(candidate.copy(ttl = if (server == "192.0.2.1" && host == V2EX_HOST) 30 else 60))
        }) { if (it.ttl == 30L) "verified" else throw IOException("handshake failed") }
        assertThat(queries).containsExactlyElementsIn(builtInResolvers.flatMap { listOf(it, it) } + "192.0.2.1").inOrder()
        assertThat(result).isEqualTo("verified" to 30L)
    }

    @Test
    fun echWithoutIpHintsUsesAddressRecordsAndShortestTtl() {
        val name = org.xbill.DNS.Name.fromString("www.v2ex.com.")
        val https = org.xbill.DNS.Record.fromString(name, org.xbill.DNS.Type.HTTPS, 1, 60, "1 . ech=AQID", name)
        val ip = InetAddress.getByName("192.0.2.5")
        val result = resolveCandidates(V2EX_HOST) { _, type ->
            when (type) {
                org.xbill.DNS.Type.HTTPS -> listOf(https)
                org.xbill.DNS.Type.A -> listOf(org.xbill.DNS.ARecord(name, 1, 10, ip))
                else -> emptyList()
            }
        }
        assertThat(result.single().address).isEqualTo(ip)
        assertThat(result.single().ttl).isEqualTo(10L)
    }

    @Test
    fun allCandidatesRejectedYieldsNoRoute() {
        assertThat(findEchRoute<String>("", { _, _ -> listOf(candidate) }) { null }).isNull()
    }
}
