package app.mystery0.nodeflow.core.link

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exLinkParserTest {
    @Test
    fun parse_topicLink() {
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/1226527"))
            .isEqualTo(V2exLink.Topic(1226527))
        assertThat(V2exLinkParser.parse("https://v2ex.com/t/1226527"))
            .isEqualTo(V2exLink.Topic(1226527))
        assertThat(V2exLinkParser.parse("http://www.v2ex.com/t/1226527"))
            .isEqualTo(V2exLink.Topic(1226527))
    }

    @Test
    fun parse_topicLinkIgnoresPageAndReplyAnchor() {
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/1226527?p=2"))
            .isEqualTo(V2exLink.Topic(1226527))
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/1226527#reply12"))
            .isEqualTo(V2exLink.Topic(1226527))
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/1226527?p=2#reply12"))
            .isEqualTo(V2exLink.Topic(1226527))
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/1226527/"))
            .isEqualTo(V2exLink.Topic(1226527))
    }

    @Test
    fun parse_nodeLink() {
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/go/python"))
            .isEqualTo(V2exLink.Node("python"))
        assertThat(V2exLinkParser.parse("https://v2ex.com/go/share/"))
            .isEqualTo(V2exLink.Node("share"))
    }

    @Test
    fun parse_memberLink() {
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/member/Livid"))
            .isEqualTo(V2exLink.Member("Livid"))
    }

    @Test
    fun parse_rejectsNonV2exHost() {
        assertThat(V2exLinkParser.parse("https://example.com/t/1226527")).isNull()
        assertThat(V2exLinkParser.parse("https://fake-v2ex.com/t/1226527")).isNull()
        assertThat(V2exLinkParser.parse("https://v2ex.com.evil.com/t/1226527")).isNull()
    }

    @Test
    fun parse_rejectsUnknownPaths() {
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/")).isNull()
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/settings")).isNull()
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/")).isNull()
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/abc")).isNull()
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/t/123/extra")).isNull()
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/go/")).isNull()
        assertThat(V2exLinkParser.parse("https://www.v2ex.com/member/")).isNull()
    }

    @Test
    fun parse_rejectsNonHttpSchemesAndMalformedUrls() {
        assertThat(V2exLinkParser.parse("ftp://www.v2ex.com/t/123")).isNull()
        assertThat(V2exLinkParser.parse("mailto:someone@example.com")).isNull()
        assertThat(V2exLinkParser.parse("not a url")).isNull()
        assertThat(V2exLinkParser.parse("")).isNull()
    }

    @Test
    fun parse_siteRelativePaths() {
        // 站内内容中的链接是相对路径，如回复里的 <a href="/t/1226857">
        assertThat(V2exLinkParser.parse("/t/1226857"))
            .isEqualTo(V2exLink.Topic(1226857))
        assertThat(V2exLinkParser.parse("/t/1226857#reply3"))
            .isEqualTo(V2exLink.Topic(1226857))
        assertThat(V2exLinkParser.parse("/go/python"))
            .isEqualTo(V2exLink.Node("python"))
        assertThat(V2exLinkParser.parse("/member/Livid"))
            .isEqualTo(V2exLink.Member("Livid"))
    }

    @Test
    fun parse_rejectsNonSitePathForms() {
        // 协议相对地址带 authority，不能当站内路径
        assertThat(V2exLinkParser.parse("//evil.com/t/123")).isNull()
        // 非 / 开头的相对片段无法确定基准，不识别
        assertThat(V2exLinkParser.parse("t/123")).isNull()
        assertThat(V2exLinkParser.parse("/settings")).isNull()
        assertThat(V2exLinkParser.parse("/t/abc")).isNull()
    }
}
