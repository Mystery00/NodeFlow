package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HtmlTextTest {
    @Test
    fun htmlWithoutImages_removesImageTagsAndEmptyImageLinks() {
        val html = """文字<a href="https://i.imgur.com/a.png"><img src="https://i.imgur.com/a.png" /></a>结束"""

        val cleaned = htmlWithoutImages(html)

        assertThat(cleaned).doesNotContain("<img")
        assertThat(cleaned).doesNotContain("https://i.imgur.com/a.png")
        assertThat(cleaned).contains("文字")
        assertThat(cleaned).contains("结束")
    }

    @Test
    fun extractHtmlImageSpecs_marksUnknownInlineImageAsCompact() {
        val html = """
            问了我 3W<a href="https://i.imgur.com/N9E3iZ2.png">
                <img src="https://i.imgur.com/N9E3iZ2.png" rel="noreferrer">
            </a>。这标准还没上实木
        """.trimIndent()

        val images = extractHtmlImageSpecs(html)

        assertThat(images).hasSize(1)
        assertThat(images.single().url).isEqualTo("https://i.imgur.com/N9E3iZ2.png")
        assertThat(images.single().compact).isTrue()
    }

    @Test
    fun extractHtmlImageSpecs_keepsEmbeddedImageAfterReplyTextAsContentImage() {
        val html = """
            @<a href="/member/qbqbqbqb">qbqbqbqb</a> #8 因为他们认为 DNS 泄露不是问题
            <a href="https://i.imgur.com/HhIXhII.png" rel="nofollow noopener">
                <img src="https://i.imgur.com/HhIXhII.png" class="embedded_image" rel="noreferrer">
            </a>
        """.trimIndent()

        val images = extractHtmlImageSpecs(html)

        assertThat(images).hasSize(1)
        assertThat(images.single().url).isEqualTo("https://i.imgur.com/HhIXhII.png")
        assertThat(images.single().compact).isFalse()
    }

    @Test
    fun extractHtmlImageSpecs_keepsImageAfterLineBreakAsContentImage() {
        val html = """
            content before image<br>
            <a href="https://i.imgur.com/content.png">
                <img src="https://i.imgur.com/content.png" class="embedded_image" rel="noreferrer">
            </a>
        """.trimIndent()

        val images = extractHtmlImageSpecs(html)

        assertThat(images).hasSize(1)
        assertThat(images.single().compact).isFalse()
    }

    @Test
    fun calculateHtmlImageLayoutSize_capsCompactImages() {
        val size = calculateHtmlImageLayoutSize(
            sourceWidthPx = 640,
            sourceHeightPx = 640,
            maxWidthDp = 360f,
            compact = true,
        )

        assertThat(size.widthDp).isEqualTo(56f)
        assertThat(size.heightDp).isEqualTo(56f)
    }

    @Test
    fun calculateHtmlImageLayoutSize_preservesLargeImageAspectRatio() {
        val size = calculateHtmlImageLayoutSize(
            sourceWidthPx = 1200,
            sourceHeightPx = 800,
            maxWidthDp = 360f,
            compact = false,
        )

        assertThat(size.widthDp).isEqualTo(360f)
        assertThat(size.heightDp).isEqualTo(240f)
    }

    @Test
    fun isZoomableHtmlImage_allowsAnyNonCompactImage() {
        // 非表情/内联的普通图片一律可点击查看，不再看尺寸
        assertThat(isZoomableHtmlImage(compact = false)).isTrue()
        assertThat(isZoomableHtmlImage(compact = true)).isFalse()
    }
}
