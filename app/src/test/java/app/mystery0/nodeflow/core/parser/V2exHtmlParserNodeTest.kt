package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 节点位面、节点详情与节点屏蔽/收藏操作解析测试。 */
class V2exHtmlParserNodeTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseNodePlanes_readsGroupedNodesFromPlanesPage() {
        val html = """
            <html><body><div id="Main">
              <div class="box">
                <div class="header flex-one-row gap10">
                  <img src="https://cdn.v2ex.com/savatar/c4ca/4238/1_large.png" />
                  混沌海
                  <div class="spacer"></div>
                  <span class="flex-one-row gap5"><span>Limbo</span><span>•</span><span class="small">110 nodes</span></span>
                </div>
                <div class="inner">
                  <a href="/go/earth" class="item_node">地球</a>
                  <a href="/go/qna" class="item_node">问与答</a>
                </div>
              </div>
              <div class="box">
                <div class="header flex-one-row gap10">
                  <img src="//cdn.v2ex.com/savatar/c81e/728d/2_large.png" />
                  机械境
                  <div class="spacer"></div>
                  <span class="flex-one-row gap5"><span>Mechanus</span><span>•</span><span class="small">784 nodes</span></span>
                </div>
                <div class="inner"><a href="/go/iphone" class="item_node">iPhone</a></div>
              </div>
            </div></body></html>
        """.trimIndent()

        val planes = parser.parseNodePlanes(html)

        assertThat(planes).hasSize(2)
        assertThat(planes.first().title).isEqualTo("混沌海")
        assertThat(planes.first().name).isEqualTo("Limbo")
        assertThat(planes.first().nodeCount).isEqualTo(110)
        assertThat(planes.first().avatarUrl).isEqualTo("https://cdn.v2ex.com/savatar/c4ca/4238/1_large.png")
        assertThat(planes.first().nodes.map { it.name }).containsExactly("earth", "qna").inOrder()
        assertThat(planes.first().nodes.map { it.title }).containsExactly("地球", "问与答").inOrder()
        assertThat(planes[1].avatarUrl).isEqualTo("https://cdn.v2ex.com/savatar/c81e/728d/2_large.png")
        assertThat(planes[1].nodes.single().name).isEqualTo("iphone")
    }

    @Test
    fun parseNodeDetail_readsJsonLdAndReturnsNullWhenMissing() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@type": "CollectionPage",
                    "name": "Android",
                    "description": "来自 <a href=\"/go/google\">Google</a> 的开放源代码智能手机平台。",
                    "image": "https://cdn.v2ex.com/navatar/d67d/8ab4/39_xxxlarge.png?m=1754172750",
                    "mainEntity": {"@type": "ItemList", "numberOfItems": 12887}
                  }
                </script>
              </head>
              <body><h1>Android</h1></body>
            </html>
        """.trimIndent()

        val node = requireNotNull(parser.parseNodeDetail("android", html))
        assertThat(node.name).isEqualTo("android")
        assertThat(node.title).isEqualTo("Android")
        assertThat(node.header).isEqualTo("来自 Google 的开放源代码智能手机平台。")
        assertThat(node.avatarUrl).isEqualTo("https://cdn.v2ex.com/navatar/d67d/8ab4/39_xxxlarge.png?m=1754172750")
        assertThat(node.topics).isEqualTo(12887)

        assertThat(parser.parseNodeDetail("android", "<html><body>empty</body></html>")).isNull()
    }

    @Test
    fun parseNodeActionOnce_extractsValidActionTokenAndRejectsMismatchedOrExternalLinks() {
        assertThat(
            parser.parseNodeActionOnce(39L, """<a href="/settings/ignore/node/39?once=12345">屏蔽节点</a>"""),
        ).isEqualTo("12345")
        assertThat(
            parser.parseNodeActionOnce(39L, """<a href="/favorite/node/39?once=67890">收藏节点</a>"""),
        ).isEqualTo("67890")

        val wrongNodeAndForm = """
            <a href="/settings/ignore/node/40?once=11111">屏蔽其他节点</a>
            <form><input type="hidden" name="once" value="22222" /></form>
        """.trimIndent()
        val externalAndPrefixed = """
            <a href="https://untrusted.example/settings/ignore/node/39?once=11111">外站链接</a>
            <a href="/unexpected/settings/ignore/node/39?once=22222">前缀错误的链接</a>
        """.trimIndent()

        assertThat(parser.parseNodeActionOnce(39L, wrongNodeAndForm)).isNull()
        assertThat(parser.parseNodeActionOnce(39L, externalAndPrefixed)).isNull()
    }
}
