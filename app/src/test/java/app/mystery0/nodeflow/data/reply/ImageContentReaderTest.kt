package app.mystery0.nodeflow.data.reply

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
import org.junit.Test

class ImageContentReaderTest {
    @Test
    fun readUpTo_stopsAtLimitWithoutReadingWholeStream() {
        val result = ByteArrayInputStream(ByteArray(20) { it.toByte() }).readUpTo(7)
        assertThat(result).hasLength(7)
        assertThat(result.toList()).containsExactlyElementsIn(ByteArray(7) { it.toByte() }.toList()).inOrder()
    }

    @Test
    fun normalizeImageFileName_replacesExtensionThatDoesNotMatchMimeType() {
        assertThat(normalizeImageFileName("photo.gif", "jpg")).isEqualTo("upload.jpg")
        assertThat(normalizeImageFileName("photo.jpeg", "jpg")).isEqualTo("photo.jpeg")
    }
}
