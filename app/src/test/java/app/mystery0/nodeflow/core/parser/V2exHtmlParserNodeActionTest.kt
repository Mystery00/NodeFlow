package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exHtmlParserNodeActionTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseNodeActionOnce_readsDirectIgnoreLink() {
        val html = """
            <a href="/settings/ignore/node/39?once=12345">屏蔽节点</a>
        """.trimIndent()

        assertThat(parser.parseNodeActionOnce(nodeId = 39L, html = html)).isEqualTo("12345")
    }

    @Test
    fun parseNodeActionOnce_readsSharedOnceFromFavoriteLink() {
        val html = """
            <a href="/favorite/node/39?once=67890">收藏节点</a>
        """.trimIndent()

        assertThat(parser.parseNodeActionOnce(nodeId = 39L, html = html)).isEqualTo("67890")
    }

    @Test
    fun parseNodeActionOnce_ignoresAnotherNodeAndUnrelatedFormToken() {
        val html = """
            <a href="/settings/ignore/node/40?once=11111">屏蔽其他节点</a>
            <form><input type="hidden" name="once" value="22222" /></form>
        """.trimIndent()

        assertThat(parser.parseNodeActionOnce(nodeId = 39L, html = html)).isNull()
    }

    @Test
    fun parseNodeActionOnce_ignoresExternalAndPrefixedActionPaths() {
        val html = """
            <a href="https://untrusted.example/settings/ignore/node/39?once=11111">外站链接</a>
            <a href="/unexpected/settings/ignore/node/39?once=22222">前缀错误的链接</a>
        """.trimIndent()

        assertThat(parser.parseNodeActionOnce(nodeId = 39L, html = html)).isNull()
    }
}
