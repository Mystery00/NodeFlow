package app.mystery0.nodeflow.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageTypeDetectorTest {

    @Test
    fun detect_identifiesJpegByMagicBytes() {
        val bytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val format = ImageTypeDetector.detect(bytes)
        assertThat(format.mimeType).isEqualTo("image/jpeg")
        assertThat(format.extension).isEqualTo("jpg")
    }

    @Test
    fun detect_identifiesPngByMagicBytes() {
        val bytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte())
        val format = ImageTypeDetector.detect(bytes)
        assertThat(format.mimeType).isEqualTo("image/png")
        assertThat(format.extension).isEqualTo("png")
    }

    @Test
    fun detect_identifiesGifByMagicBytes() {
        val bytes87 = "GIF87a...".toByteArray()
        val format87 = ImageTypeDetector.detect(bytes87)
        assertThat(format87.mimeType).isEqualTo("image/gif")
        assertThat(format87.extension).isEqualTo("gif")

        val bytes89 = "GIF89a...".toByteArray()
        val format89 = ImageTypeDetector.detect(bytes89)
        assertThat(format89.mimeType).isEqualTo("image/gif")
        assertThat(format89.extension).isEqualTo("gif")
    }

    @Test
    fun detect_identifiesWebpByMagicBytes() {
        // RIFF (4 bytes) + file length (4 bytes) + WEBP (4 bytes)
        val riff = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
        val length = byteArrayOf(0, 0, 0, 0)
        val webp = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
        val bytes = riff + length + webp
        val format = ImageTypeDetector.detect(bytes)
        assertThat(format.mimeType).isEqualTo("image/webp")
        assertThat(format.extension).isEqualTo("webp")
    }

    @Test
    fun detect_identifiesBmpByMagicBytes() {
        val bytes = byteArrayOf(0x42.toByte(), 0x4D.toByte(), 0, 0)
        val format = ImageTypeDetector.detect(bytes)
        assertThat(format.mimeType).isEqualTo("image/bmp")
        assertThat(format.extension).isEqualTo("bmp")
    }

    @Test
    fun detect_fallsBackToContentTypeWhenMagicBytesUnrecognized() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val format = ImageTypeDetector.detect(bytes, headerContentType = "image/png; charset=utf-8")
        assertThat(format.mimeType).isEqualTo("image/png")
        assertThat(format.extension).isEqualTo("png")
    }

    @Test
    fun detect_fallsBackToUrlExtensionWhenBytesAndContentTypeUnrecognized() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val format = ImageTypeDetector.detect(bytes, url = "https://example.com/images/avatar.webp?query=1")
        assertThat(format.mimeType).isEqualTo("image/webp")
        assertThat(format.extension).isEqualTo("webp")
    }

    @Test
    fun detect_defaultsToJpegWhenAllUnknown() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val format = ImageTypeDetector.detect(bytes, url = "https://example.com/no-extension")
        assertThat(format.mimeType).isEqualTo("image/jpeg")
        assertThat(format.extension).isEqualTo("jpg")
    }
}
