package app.mystery0.nodeflow.core.parser

import app.mystery0.nodeflow.core.common.NodeFlowException
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FavoriteTopicsParserTest {
    private val parser = V2exHtmlParser()

    @Test
    fun readsListWhenHeadingAndTopicsUseSeparateBoxes() {
        val html = "<div id='Main'><div class='box'><div class='cell'><h2>收藏</h2></div></div><div class='box'>${cell(11)}<a href='?p=2'>2</a></div></div>"
        val page = parser.parseFavoriteTopicsPage(html, 1)
        assertThat(page.topics.single().id).isEqualTo(11)
        assertThat(page.nextPage).isEqualTo(2)
    }

    @Test
    fun recognizesCurrentDesktopHeadingInsideCell() {
        // 真实桌面模板使用 .cell > h2，不提供 .header；仅保留识别所需的通用标题。
        val html = "<div id='Main'><div class='box'><div class='cell'><h2>收藏</h2></div>${cell(11)}</div><div class='box'>其他内容</div></div>"
        assertThat(parser.parseFavoriteTopicsPage(html, 1).topics.single().id).isEqualTo(11)
    }

    @Test
    fun parsesTopicsAndPaginationWithoutSidebarOrInferredPinnedStatus() {
        val page = parser.parseFavoriteTopicsPage(pageHtml("""
            ${cell(11, "2026-08-01 10:00:00 +08:00")}
            ${cell(12, "2026-08-02 10:00:00 +08:00")}
            <a class="page_normal" href="/my/topics?p=2">2</a>
            <a class="page_normal" href="/my/topics?p=8">8</a>
        """) + "<div id='Rightbar'>${cell(99)}</div>", 1)

        assertThat(page.topics.map { it.id }).containsExactly(11L, 12L).inOrder()
        assertThat(page.topics.none { it.isPinned }).isTrue()
        assertThat(page.topics.first().author.username).isEqualTo("tester")
        assertThat(page.topics.first().node.name).isEqualTo("android")
        assertThat(page.nextPage).isEqualTo(2)
    }

    @Test
    fun readsPageInputAndPreservesLastPage() {
        val html = pageHtml("${cell(11)}<input class='page_input' value='2' max='3'>")
        assertThat(parser.parseFavoriteTopicsPage(html, 2).nextPage).isEqualTo(3)
        assertThat(parser.parseFavoriteTopicsPage(pageHtml(cell(12)), 3).nextPage).isNull()
    }

    @Test
    fun acceptsRecognizedEmptyCollection() {
        val page = parser.parseFavoriteTopicsPage(pageHtml("<div class='inner'>暂无收藏</div>"), 1)
        assertThat(page.topics).isEmpty()
        assertThat(page.nextPage).isNull()
    }

    @Test
    fun supportsItemTitleLinksWithoutTopicLinkClass() {
        val page = parser.parseFavoriteTopicsPage(pageHtml(cell(11).replace("class='topic-link'", "")), 1)
        assertThat(page.topics.single().id).isEqualTo(11)
    }

    @Test
    fun rejectsUnknownAndMalformedPages() {
        listOf(
            "<div id='Main'><div class='box'>未知页面</div></div>",
            pageHtml(cell(11).replace("/t/11", "/t/invalid")),
            pageHtml("<div class='cell item'>损坏的条目</div>"),
        ).forEach { html ->
            val error = runCatching { parser.parseFavoriteTopicsPage(html, 1) }.exceptionOrNull()
            assertThat(error).isInstanceOf(NodeFlowException::class.java)
        }
    }

    @Test
    fun ignoresForeignPaginationLinks() {
        val page = parser.parseFavoriteTopicsPage(pageHtml("""
            ${cell(11)}
            <a href='https://example.com/my/topics?p=2'>下一页</a>
            <a href='/recent?p=2'>下一页</a>
        """), 1)
        assertThat(page.nextPage).isNull()
    }

    private fun pageHtml(content: String) =
        "<div id='Main'><div class='box'><div class='header'><a href='/'>V2EX</a> › 我收藏的主题</div>$content</div></div>"

    private fun cell(id: Long, time: String = "") = """
        <div class='cell item'><span class='item_title'><a class='topic-link' href='/t/$id'>测试主题</a></span>
        <span class='topic_info'><a class='node' href='/go/android'>Android</a> ·
        <strong><a href='/member/tester'>tester</a></strong><span title='$time'>刚刚</span></span></div>
    """
}
