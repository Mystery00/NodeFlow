package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exHtmlParserTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseNodePlanes_readsGroupedNodesFromPlanesPage() {
        val html = """
            <html>
              <body>
                <div id="Main">
                  <div class="box">
                    <div class="header flex-one-row gap10">
                      <img src="https://cdn.v2ex.com/savatar/c4ca/4238/1_large.png" />
                      混沌海
                      <div class="spacer"></div>
                      <span class="flex-one-row gap5">
                        <span>Limbo</span>
                        <span>•</span>
                        <span class="small">110 nodes</span>
                      </span>
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
                      <span class="flex-one-row gap5">
                        <span>Mechanus</span>
                        <span>•</span>
                        <span class="small">784 nodes</span>
                      </span>
                    </div>
                    <div class="inner">
                      <a href="/go/iphone" class="item_node">iPhone</a>
                    </div>
                  </div>
                </div>
              </body>
            </html>
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
    fun parseTopicList_readsTopicCellsFromNodePage() {
        val html = """
            <html>
              <body>
                <div class="cell from_332855 t_1206122">
                  <table>
                    <tr>
                      <td><a href="/member/Junian"><img src="https://cdn.v2ex.com/avatar/normal.png" class="avatar" alt="Junian" /></a></td>
                      <td>
                        <span class="item_title"><a href="/t/1206122#reply17" class="topic-link" id="topic-link-1206122">把电脑伪装成电视，用 DLNA 投屏拿到视频号直播流地址</a></span>
                        <span class="topic_info"><strong><a href="/member/Junian">Junian</a></strong> &nbsp;•&nbsp; <span title="2026-05-14 13:54:31 +08:00">May 14</span> &nbsp;•&nbsp; Lastly replied by <strong><a href="/member/godall">godall</a></strong></span>
                      </td>
                      <td><a href="/t/1206122#reply17" class="count_livid">17</a></td>
                    </tr>
                  </table>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topics = parser.parseTopicList(html, sourceNodeName = "python")

        assertThat(topics).hasSize(1)
        assertThat(topics.first().id).isEqualTo(1206122)
        assertThat(topics.first().title).isEqualTo("把电脑伪装成电视，用 DLNA 投屏拿到视频号直播流地址")
        assertThat(topics.first().author.username).isEqualTo("Junian")
        assertThat(topics.first().replyCount).isEqualTo(17)
        assertThat(topics.first().node.name).isEqualTo("python")
        assertThat(topics.first().avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar/normal.png")
    }

    @Test
    fun extractImageUrls_readsImagesAndLinkedImageUrls() {
        val html = """
            <p>正文</p>
            <p><img src="//cdn.v2ex.com/image/a.png" /></p>
            <p><a href="https://example.com/photo.jpg">https://example.com/photo.jpg</a></p>
            <p><a href="https://example.com/page">普通链接</a></p>
        """.trimIndent()

        val urls = parser.extractImageUrls(html)

        assertThat(urls).containsExactly(
            "https://cdn.v2ex.com/image/a.png",
            "https://example.com/photo.jpg",
        ).inOrder()
    }
}
