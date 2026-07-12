package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exHtmlDocumentTest {
    @Test
    fun buildV2exHtmlDocument_wrapsMarkdownHtmlWithReadableStyles() {
        val document = buildV2exHtmlDocument(
            bodyHtml = """
                <h2>标题</h2>
                <p>正文 <a href="https://example.com">链接</a></p>
                <blockquote>引用</blockquote>
                <pre><code>val answer = 42</code></pre>
                <table><tr><td>A</td><td>B</td></tr></table>
                <p><img src="/sample.png" /></p>
                <script>alert('x')</script>
            """.trimIndent(),
            colors = V2exHtmlColors(
                text = "#111111",
                secondaryText = "#666666",
                link = "#0066cc",
                background = "#ffffff",
                codeBackground = "#f3f4f6",
                quoteBackground = "#f7f8fa",
                border = "#dddddd",
            ),
        )

        assertThat(document).contains("""<meta name="viewport"""")
        assertThat(document).contains("""target="_blank"""")
        assertThat(document).contains("pre {")
        assertThat(document).contains("blockquote {")
        assertThat(document).contains("table {")
        assertThat(document).contains("img {")
        assertThat(document).contains("val answer = 42")
        assertThat(document).doesNotContain("<script>")
    }

    @Test
    fun buildV2exHtmlDocument_containsMarginsWithFlowRoot() {
        // markdown 包装层里首尾元素的外边距若塌陷逃逸出 .nodeflow-content，
        // getBoundingClientRect 会漏掉这部分高度，导致正文底部被截断
        val document = buildV2exHtmlDocument(
            bodyHtml = "<p>正文</p>",
            colors = V2exHtmlColors(
                text = "#111111",
                secondaryText = "#666666",
                link = "#0066cc",
                background = "#ffffff",
                codeBackground = "#f3f4f6",
                quoteBackground = "#f7f8fa",
                border = "#dddddd",
            ),
        )

        assertThat(document).contains("display: flow-root;")
    }
}
