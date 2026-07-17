package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exContentLinkifyTest {
    @Test
    fun linkify_convertsPlainRelativeTopicPathToAbsoluteAnchor() {
        val html = linkifyV2exTopicReferences("/t/1226857 <br> 有讨论的，和楼主观点相反")

        assertThat(html).contains("""<a href="https://www.v2ex.com/t/1226857">/t/1226857</a>""")
        assertThat(html).contains("有讨论的，和楼主观点相反")
    }

    @Test
    fun linkify_convertsHostVariantsAndKeepsOriginalText() {
        val html = linkifyV2exTopicReferences(
            "<p>见 www.v2ex.com/t/100 和 https://www.v2ex.com/t/200 以及 v2ex.com/t/300</p>",
        )

        assertThat(html).contains("""<a href="https://www.v2ex.com/t/100">www.v2ex.com/t/100</a>""")
        assertThat(html).contains("""<a href="https://www.v2ex.com/t/200">https://www.v2ex.com/t/200</a>""")
        assertThat(html).contains("""<a href="https://www.v2ex.com/t/300">v2ex.com/t/300</a>""")
    }

    @Test
    fun linkify_keepsReplyAnchorFragmentInHref() {
        val html = linkifyV2exTopicReferences("<p>看这楼 /t/1226857#reply3</p>")

        assertThat(html).contains("""<a href="https://www.v2ex.com/t/1226857#reply3">/t/1226857#reply3</a>""")
    }

    @Test
    fun linkify_handlesMultipleMatchesInOneTextNode() {
        val html = linkifyV2exTopicReferences("<p>对比 /t/1 和 /t/2 两帖</p>")

        assertThat(html).contains("""<a href="https://www.v2ex.com/t/1">/t/1</a>""")
        assertThat(html).contains("""<a href="https://www.v2ex.com/t/2">/t/2</a>""")
        assertThat(html).contains("对比")
        assertThat(html).contains("两帖")
    }

    @Test
    fun linkify_skipsExistingAnchorsAndCodeBlocks() {
        val input = """<a href="/t/999">/t/999</a><pre>/t/888</pre><code>/t/777</code>"""

        val html = linkifyV2exTopicReferences(input)

        assertThat(html).doesNotContain("https://www.v2ex.com/t/999")
        assertThat(html).doesNotContain("https://www.v2ex.com/t/888")
        assertThat(html).doesNotContain("https://www.v2ex.com/t/777")
    }

    @Test
    fun linkify_ignoresNonTopicLookalikes() {
        val input = "<p>foo/t/123 example.com/t/456 /t/abc /tt/789</p>"

        val html = linkifyV2exTopicReferences(input)

        assertThat(html).doesNotContain("<a")
    }

    @Test
    fun linkify_returnsInputUntouchedWhenNoTopicPath() {
        val input = "<p>没有链接的普通回复</p>"

        assertThat(linkifyV2exTopicReferences(input)).isEqualTo(input)
    }
}
