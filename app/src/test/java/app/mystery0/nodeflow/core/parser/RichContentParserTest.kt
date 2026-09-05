package app.mystery0.nodeflow.core.parser

import app.mystery0.nodeflow.core.model.RichContentBlock
import app.mystery0.nodeflow.core.model.RichInline
import app.mystery0.nodeflow.core.model.plainText
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichContentParserTest {
    @Test
    fun parse_keepsNestedInlineFormattingInOneParagraph() {
        val document = RichContentParser.parse(
            "<p>前缀<strong>粗体 <a href='/t/12'>链接</a></strong>后缀</p>",
        )

        assertThat(document.blocks).hasSize(1)
        val paragraph = document.blocks.single() as RichContentBlock.Paragraph
        assertThat(paragraph.content.filterIsInstance<RichInline.Text>().joinToString("") { it.value })
            .isEqualTo("前缀粗体 链接后缀")
        assertThat(paragraph.content.filterIsInstance<RichInline.Text>().single { it.value == "链接" }.linkUrl)
            .isEqualTo("https://www.v2ex.com/t/12")
        assertThat(paragraph.content.filterIsInstance<RichInline.Text>().single { it.value == "粗体 " }.style.bold)
            .isTrue()
    }

    @Test
    fun parse_preservesTextImageAndFollowingTextOrder() {
        val document = RichContentParser.parse(
            "<p>之前<img class='embedded_image' src='/a.png'>之后</p>",
        )

        assertThat(document.blocks.map { it::class.simpleName })
            .containsExactly("Paragraph", "Image", "Paragraph")
            .inOrder()
        assertThat((document.blocks[1] as RichContentBlock.Image).image.url)
            .isEqualTo("https://www.v2ex.com/a.png")
    }

    @Test
    fun parse_keepsCompactImageInsideParagraph() {
        val document = RichContentParser.parse(
            "<p>状态<img class='emoji' src='/smile.png' alt='🙂' width='20' height='20'>正常</p>",
        )

        assertThat(document.blocks).hasSize(1)
        val paragraph = document.blocks.single() as RichContentBlock.Paragraph
        assertThat(paragraph.content.filterIsInstance<RichInline.InlineImage>()).hasSize(1)
    }

    @Test
    fun parse_dropsExecutableContentButKeepsUnknownWrapperChildren() {
        val document = RichContentParser.parse(
            "<custom>保留<script>bad()</script><b>文字</b></custom>",
        )

        assertThat(document.plainText()).isEqualTo("保留文字")
    }

    @Test
    fun parse_extractsTableSpansVideoAndIframe() {
        val html = """
            <table>
              <caption>参数</caption>
              <tr><th colspan="2">标题</th></tr>
              <tr><td rowspan="2">A</td><td>B</td></tr>
            </table>
            <video poster="/poster.jpg"><source src="/movie.mp4" type="video/mp4"></video>
            <iframe title="演示" src="https://example.com/embed/1"></iframe>
        """.trimIndent()

        val document = RichContentParser.parse(html)

        val table = document.blocks[0] as RichContentBlock.Table
        assertThat(table.caption.filterIsInstance<RichInline.Text>().joinToString("") { it.value })
            .isEqualTo("参数")
        assertThat(table.rows[0].cells.single().colSpan).isEqualTo(2)
        assertThat(table.rows[0].cells.single().isHeader).isTrue()
        assertThat(table.rows[1].cells[0].rowSpan).isEqualTo(2)
        val video = document.blocks[1] as RichContentBlock.Video
        assertThat(video.video.sources.single().url).isEqualTo("https://www.v2ex.com/movie.mp4")
        assertThat(video.video.posterUrl).isEqualTo("https://www.v2ex.com/poster.jpg")
        val iframe = document.blocks[2] as RichContentBlock.IframePlaceholder
        assertThat(iframe.embed.url).isEqualTo("https://example.com/embed/1")
    }

    @Test
    fun parse_preservesNestedListsAndStartingNumber() {
        val document = RichContentParser.parse(
            "<ol start='3'><li>甲<ul><li>子项</li></ul></li><li>乙</li></ol>",
        )

        val list = document.blocks.single() as RichContentBlock.ListBlock
        assertThat(list.ordered).isTrue()
        assertThat(list.start).isEqualTo(3)
        assertThat(list.items).hasSize(2)
        assertThat(list.items[0].blocks.filterIsInstance<RichContentBlock.ListBlock>()).hasSize(1)
    }

    @Test
    fun parse_convertsCustomImageHostLinkToFollowingImageBlock() {
        val document = RichContentParser.parse(
            "<p><a href='https://img.example.com/asset'>查看图片</a></p>",
            customImageHosts = setOf("example.com"),
        )

        assertThat(document.blocks).hasSize(2)
        assertThat(document.blocks[0].plainText()).isEqualTo("查看图片")
        assertThat((document.blocks[1] as RichContentBlock.Image).image.url)
            .isEqualTo("https://img.example.com/asset")
    }

    @Test
    fun parse_rejectsUnsafeMediaAndIframeUrls() {
        val document = RichContentParser.parse(
            "<img src='javascript:bad'><video src='file:///tmp/a.mp4'></video><iframe src='javascript:bad'></iframe>",
        )

        assertThat(document.blocks.filterIsInstance<RichContentBlock.Image>()).isEmpty()
        assertThat(document.blocks.filterIsInstance<RichContentBlock.Video>().single().video.sources).isEmpty()
        assertThat(document.blocks.filterIsInstance<RichContentBlock.IframePlaceholder>().single().embed.url)
            .isNull()
    }
}
