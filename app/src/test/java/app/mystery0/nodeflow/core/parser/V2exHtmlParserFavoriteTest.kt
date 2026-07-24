package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exHtmlParserFavoriteTest {
    private val parser = V2exHtmlParser()
    private val topicId = 1234567L

    /** 未收藏状态：页面包含「加入收藏」链接，href 为 /favorite/topic/{id}?once=... */
    @Test
    fun parseTopicHtml_notFavorited_extractsFalseAndOnce() {
        val html = topicHtmlWithFavoriteLink(
            favoriteHref = "/favorite/topic/$topicId?once=98765",
            favoriteText = "加入收藏",
        )
        val result = parser.parseTopicHtml(topicId, html)

        assertThat(result).isNotNull()
        assertThat(result!!.isFavorited).isFalse()
        assertThat(result.favoriteOnce).isEqualTo("98765")
    }

    /** 已收藏状态：页面包含「取消收藏」链接，href 为 /unfavorite/topic/{id}?once=... */
    @Test
    fun parseTopicHtml_favorited_extractsTrueAndOnce() {
        val html = topicHtmlWithFavoriteLink(
            favoriteHref = "/unfavorite/topic/$topicId?once=11111",
            favoriteText = "取消收藏",
        )
        val result = parser.parseTopicHtml(topicId, html)

        assertThat(result).isNotNull()
        assertThat(result!!.isFavorited).isTrue()
        assertThat(result.favoriteOnce).isEqualTo("11111")
    }

    /** 未登录状态：页面不包含收藏链接 */
    @Test
    fun parseTopicHtml_noFavoriteLink_returnsNullState() {
        val html = topicHtmlWithFavoriteLink(favoriteHref = null, favoriteText = null)
        val result = parser.parseTopicHtml(topicId, html)

        assertThat(result).isNotNull()
        assertThat(result!!.isFavorited).isNull()
        assertThat(result.favoriteOnce).isNull()
    }

    /** 收藏链接缺少 once 参数时，isFavorited 仍可识别，once 为 null */
    @Test
    fun parseTopicHtml_favoriteLinkWithoutOnce_extractsStateButNullOnce() {
        val html = topicHtmlWithFavoriteLink(
            favoriteHref = "/favorite/topic/$topicId",
            favoriteText = "加入收藏",
        )
        val result = parser.parseTopicHtml(topicId, html)

        assertThat(result).isNotNull()
        assertThat(result!!.isFavorited).isFalse()
        assertThat(result.favoriteOnce).isNull()
    }

    private fun topicHtmlWithFavoriteLink(
        favoriteHref: String?,
        favoriteText: String?,
    ): String {
        val favoriteLinkHtml = if (favoriteHref != null && favoriteText != null) {
            """<a href="$favoriteHref" class="tb">$favoriteText</a>"""
        } else {
            ""
        }
        return """
            <html>
              <body>
                <div id="Main">
                  <div class="box">
                    <div class="header">
                      <h1>测试主题标题</h1>
                      <small class="gray">
                        <a href="/member/testuser">testuser</a>
                        · <span title="2026-07-20 10:30:00 +08:00">1 hour ago</span>
                      </small>
                    </div>
                    <div class="cell">
                      <div class="topic_content">
                        <div class="markdown_body"><p>正文内容</p></div>
                      </div>
                    </div>
                    <div class="topic_buttons">
                      $favoriteLinkHtml
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()
    }
}
