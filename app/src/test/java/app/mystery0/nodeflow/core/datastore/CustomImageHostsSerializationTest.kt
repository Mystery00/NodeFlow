package app.mystery0.nodeflow.core.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CustomImageHostsSerializationTest {
    @Test
    fun roundTrip_keepsOrderAndContent() {
        val hosts = listOf("example.com", "img.foo.net")
        assertThat(decodeCustomImageHosts(encodeCustomImageHosts(hosts))).isEqualTo(hosts)
    }

    @Test
    fun decode_handlesNullAndBlank() {
        assertThat(decodeCustomImageHosts(null)).isEmpty()
        assertThat(decodeCustomImageHosts("")).isEmpty()
        assertThat(decodeCustomImageHosts("\n\n")).isEmpty()
    }

    @Test
    fun decode_skipsBlankLines() {
        assertThat(decodeCustomImageHosts("a.com\n\nb.com\n")).isEqualTo(listOf("a.com", "b.com"))
    }
}
