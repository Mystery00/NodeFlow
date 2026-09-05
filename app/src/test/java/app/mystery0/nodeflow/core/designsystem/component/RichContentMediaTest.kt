package app.mystery0.nodeflow.core.designsystem.component

import app.mystery0.nodeflow.core.model.RichVideoSource
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichContentMediaTest {
    @Test
    fun imageSizeCache_reusesResolvedAspectRatioAfterReattach() {
        val cache = RichContentImageSizeCache()
        cache.put("https://example.com/a.jpg", width = 1200, height = 800)

        assertThat(cache.aspectRatio("https://example.com/a.jpg", width = null, height = null))
            .isWithin(0.001f)
            .of(1.5f)
    }

    @Test
    fun imageSizeCache_prefersResolvedDimensionsOverDeclaredDimensions() {
        val cache = RichContentImageSizeCache()
        cache.put("https://example.com/a.jpg", width = 1200, height = 800)

        assertThat(cache.aspectRatio("https://example.com/a.jpg", width = 400, height = 400))
            .isWithin(0.001f)
            .of(1.5f)
    }

    @Test
    fun imageSizeCache_usesDeclaredDimensionsBeforeImageLoads() {
        val cache = RichContentImageSizeCache()

        assertThat(cache.aspectRatio("https://example.com/a.jpg", width = 400, height = 400))
            .isEqualTo(1f)
    }

    @Test
    fun imageSizeCache_keepsResolvedRatioForLongImages() {
        val cache = RichContentImageSizeCache()
        cache.put("https://example.com/long.png", width = 500, height = 5000)

        assertThat(cache.aspectRatio("https://example.com/long.png", width = null, height = null))
            .isWithin(0.001f)
            .of(0.1f)
    }

    @Test
    fun selectVideoSource_prefersDeclaredPlayableMp4ThenFallsBackInOrder() {
        val sources = listOf(
            RichVideoSource("https://example.com/a.bin", null),
            RichVideoSource("https://example.com/a.m3u8", "application/x-mpegURL"),
            RichVideoSource("https://example.com/a.mp4", "video/mp4"),
        )

        assertThat(selectVideoSource(sources)?.url).endsWith("a.mp4")
        assertThat(selectVideoSource(listOf(sources[0]))).isEqualTo(sources[0])
    }

    @Test
    fun selectVideoSource_acceptsParameterizedMimeWithoutFileExtension() {
        val source = RichVideoSource(
            url = "https://example.com/video?id=1",
            mimeType = "video/mp4; codecs=avc1.640028",
        )

        assertThat(
            selectVideoSource(
                listOf(RichVideoSource("https://example.com/fallback.bin"), source),
            ),
        ).isEqualTo(source)
    }
}
