package app.mystery0.nodeflow.core.parser

import app.mystery0.nodeflow.core.model.RichContentBlock
import app.mystery0.nodeflow.core.model.RichInline
import app.mystery0.nodeflow.core.model.containsBase64
import app.mystery0.nodeflow.core.model.plainText
import com.google.common.truth.Truth.assertThat
import org.jsoup.Jsoup
import org.junit.Test
import java.util.Base64

class ContentTextRecognizerTest {
    @Test fun links_respectPriorityPunctuationAndEmailBoundaries() {
        val text = "看 (https://example.com/a_(b)). www.example.org/x，me+tag@example.com。 /t/123#reply2"
        val matches = recognizeContentText(text)
        assertThat(matches.map { text.substring(it.start, it.end) }).containsExactly(
            "https://example.com/a_(b)", "www.example.org/x", "me+tag@example.com", "/t/123#reply2",
        ).inOrder()
        assertThat(recognizeContentText("https://example.com/?email=me@example.com")).hasSize(1)
        assertThat(recognizeContentText("bad..user@example.com @user@example.com")).isEmpty()
        assertThat(recognizeContentText("www.example.com/path/me@example.com").single().kind)
            .isEqualTo(ContentTextKind.Url)
    }

    @Test fun safeLinks_rejectDangerousSchemesAndImplicitMailParameters() {
        listOf("javascript:alert(1)", "data:text/plain,hi", "intent://x", "file:///tmp/a",
            "https://trusted.example@evil.example", "https://example.com/%0a", "https://example.com\\evil",
            "mailto:a@example.com?body=hidden", "mailto:a%0d%0a@example.com", "https:///missing",
        ).forEach { assertThat(safeContentUrl(it)).isNull() }
        assertThat(safeContentUrl("HTTPS://example.com/a")).isNotNull()
        assertThat(safeContentUrl("mailto:me+tag@example.com")).isNotNull()
        val email = recognizeContentText("a?b#c@example.com").single()
        assertThat(email.target).isEqualTo("mailto:a%3Fb%23c@example.com")
        assertThat(safeContentUrl(email.target!!)).isNotNull()
    }

    @Test fun base64_requiresCanonicalReadableBoundedUtf8() {
        listOf("你好，世界", "https://example.com", "line one\nline two", "<b>只是文字</b>", "emoji 🙂 text")
            .forEach { assertThat(decodeReadableBase64(encode(it))).isEqualTo(it) }
        listOf("SGVsbG8", "SGVsbG8=", "aGVsbG8gd29ybGQ===", "0123456789abcdef", "/////w==",
            encode("hello\u0000world"), encode("hello\u202Eworld"), encode(" ".repeat(12)), encode("a".repeat(7000)),
            "aGVsbG8gd29ybGR=",
        ).forEach { assertThat(decodeReadableBase64(it)).isNull() }
        val encoded = encode("hello world")
        assertThat(recognizeContentText("($encoded)").single().kind).isEqualTo(ContentTextKind.Base64)
        assertThat(recognizeContentText("https://example.com/$encoded").single().kind).isEqualTo(ContentTextKind.Url)
        assertThat(recognizeContentText("_${encoded}_")).isEmpty()
        assertThat(recognizeContentText(encoded, includeBase64 = false)).isEmpty()
    }

    @Test fun html_enhancementSkipsExistingLinksCodeAndAttributes() {
        val encoded = encode("hello world")
        val html = "<p><b>https://example.com</b> $encoded</p>" +
            "<a href='/member/user'>$encoded</a><code>$encoded https://code.example</code>" +
            "<pre>$encoded</pre><img src='https://image.example/a.png' alt='$encoded'>"
        val document = RichContentParser.parse(html, contentKey = "topic:1")
        assertThat(document.containsBase64()).isTrue()
        val paragraphs = document.blocks.filterIsInstance<RichContentBlock.Paragraph>()
        assertThat(paragraphs.flatMap { it.content }.filterIsInstance<RichInline.Base64Text>()).hasSize(1)
        val linked = paragraphs.flatMap { it.content }.filterIsInstance<RichInline.Text>()
        assertThat(linked.single { it.value == "https://example.com" }.style.bold).isTrue()
        assertThat(linked.single { it.value.contains("https://code.example") }.linkUrl).isNull()
        assertThat(document.plainText()).contains(encoded)
        val styled = RichContentParser.parse("<b>$encoded</b>").blocks.single() as RichContentBlock.Paragraph
        assertThat((styled.content.single() as RichInline.Base64Text).style.bold).isTrue()
        val dom = Jsoup.parseBodyFragment(linkifyContentHtml(html))
        assertThat(dom.select("a")).hasSize(2)
        assertThat(dom.select("a")[1].attr("href")).isEqualTo("https://www.v2ex.com/member/user")
        assertThat(dom.selectFirst("img")!!.attr("alt")).isEqualTo(encoded)
    }

    @Test fun keys_areStablePerOwnerContentAndOccurrence() {
        val text = "${encode("hello world")} ${encode("hello world")}"
        fun keys(owner: String, html: String = text) = (RichContentParser.parse(html, contentKey = owner).blocks.single()
            as RichContentBlock.Paragraph).content.filterIsInstance<RichInline.Base64Text>().map { it.key }
        assertThat(keys("reply:1")).isEqualTo(keys("reply:1"))
        assertThat(keys("reply:1").distinct()).hasSize(2)
        assertThat(keys("reply:1")).containsNoneIn(keys("reply:2"))
        assertThat(keys("reply:1")).containsNoneIn(keys("reply:1", "$text changed"))
    }

    @Test fun recognition_doesNotCreateImageLoadsOrDuplicateReplyImages() {
        val hosts = setOf("i.imgur.com")
        val html = "<p>https://i.imgur.com/a.png ${encode("hello world")}</p>"
        assertThat(RichContentParser.parse(html, customImageHosts = hosts).blocks.filterIsInstance<RichContentBlock.Image>()).isEmpty()
        val linked = "<a href='https://i.imgur.com/a.png'>picture</a>"
        assertThat(RichContentParser.parse(linked, customImageHosts = hosts).blocks.filterIsInstance<RichContentBlock.Image>()).hasSize(1)
        assertThat(RichContentParser.parse(linked, customImageHosts = hosts, renderLinkedImages = false).blocks
            .filterIsInstance<RichContentBlock.Image>()).isEmpty()
    }

    private fun encode(text: String) = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}
