package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exHtmlParserTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseSignInChallenge_readsDynamicFieldsAndCaptchaPath() {
        val html = """
            <html>
              <body>
                <form method="post" action="/signin">
                  <input type="text" class="sl" name="user_field_hash" value="" placeholder="Username or Email" />
                  <input type="password" class="sl" name="pass_field_hash" value="" />
                  <img id="captcha-image" width="320" height="80" src="/_captcha" alt="CAPTCHA">
                  <input type="text" class="sl" name="captcha_field_hash" value="" placeholder="Enter the code above, click to change">
                  <input type="hidden" value="66994" name="once" />
                  <input type="hidden" value="/" name="next" />
                </form>
              </body>
            </html>
        """.trimIndent()

        val challenge = parser.parseSignInChallenge(html)

        assertThat(challenge).isNotNull()
        assertThat(challenge!!.usernameField).isEqualTo("user_field_hash")
        assertThat(challenge.passwordField).isEqualTo("pass_field_hash")
        assertThat(challenge.captchaField).isEqualTo("captcha_field_hash")
        assertThat(challenge.once).isEqualTo("66994")
        assertThat(challenge.next).isEqualTo("/")
        assertThat(challenge.captchaPath).isEqualTo("/_captcha")
    }

    @Test
    fun parseSignInChallenge_readsCurrentChineseSignInForm() {
        val html = """
            <html>
              <body>
                <form method="post" action="/signin">
                  <input type="hidden" name="next" value="/mission/daily" />
                  <input type="text" class="sl" name="user_hash" value="" />
                  <input type="hidden" value="91811" name="once" />
                  <input type="password" class="sl" name="pass_hash" value="" />
                  <img id="captcha-image" width="280" height="80" src="/_captcha" alt="CAPTCHA">
                  <input type="text" class="sl" name="captcha_hash" value="" placeholder="请输入上图中的验证码" />
                </form>
              </body>
            </html>
        """.trimIndent()

        val challenge = parser.parseSignInChallenge(html)

        assertThat(challenge).isNotNull()
        assertThat(challenge!!.usernameField).isEqualTo("user_hash")
        assertThat(challenge.passwordField).isEqualTo("pass_hash")
        assertThat(challenge.captchaField).isEqualTo("captcha_hash")
        assertThat(challenge.once).isEqualTo("91811")
        assertThat(challenge.next).isEqualTo("/mission/daily")
    }

    @Test
    fun parseLoginAccount_readsUserFromDailyPage() {
        val html = """
            <html>
              <body>
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <img src="//cdn.v2ex.com/avatar/current_normal.png" />
                </div>
                <div class="cell">
                  <input type="button" onclick="location.href = '/mission/daily/redeem?once=12345';" />
                </div>
              </body>
            </html>
        """.trimIndent()

        val account = parser.parseLoginAccount(html)

        assertThat(account).isNotNull()
        assertThat(account!!.username).isEqualTo("currentUser")
        assertThat(account.avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar/current_large.png")
    }

    @Test
    fun parseTwoFactorChallenge_readsOnceFromTwoFactorForm() {
        val html = """
            <html>
              <body>
                <form method="post" action="/2fa?next=/mission/daily">
                  <table>
                    <tr><td>两步验证</td></tr>
                    <tr><td><input type="hidden" name="once" value="24680" /></td></tr>
                  </table>
                </form>
              </body>
            </html>
        """.trimIndent()

        val challenge = parser.parseTwoFactorChallenge(html)

        assertThat(challenge).isNotNull()
        assertThat(challenge!!.once).isEqualTo("24680")
        assertThat(challenge.title).contains("两步验证")
    }

    @Test
    fun parseSignInChallenge_returnsNullWhenRequiredFieldsAreMissing() {
        val challenge = parser.parseSignInChallenge("<html><body>No login form</body></html>")

        assertThat(challenge).isNull()
    }

    @Test
    fun parseCurrentUsername_readsLoggedInMemberLinkWhenSignOutExists() {
        val html = """
            <html>
              <body>
                <div id="Main">
                  <a href="/member/topic-author">topic-author</a>
                </div>
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <a href="/signout?once=12345">Sign Out</a>
                </div>
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.parseCurrentUsername(html)).isEqualTo("currentUser")
        assertThat(parser.isLoggedInAs(html, "currentUser")).isTrue()
    }

    @Test
    fun parseCurrentUsername_returnsNullWhenHomePageIsAnonymous() {
        val html = """
            <html>
              <body>
                <a href="/signin">Sign In</a>
                <a href="/member/other">other</a>
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.parseCurrentUsername(html)).isNull()
        assertThat(parser.isLoggedInAs(html, "currentUser")).isFalse()
    }

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

    @Test
    fun parseTopicHtml_readsSupplementalMetadataFromTopicPage() {
        val html = """
            <html>
              <head>
                <meta property="article:section" content="程序员" />
                <script type="application/ld+json">
                  {
                    "comment": [
                      {
                        "@type": "Comment",
                        "text": "普通回复"
                      },
                      {
                        "@type": "Comment",
                        "text": "热门回复 A",
                        "interactionStatistic": {
                          "@type": "InteractionCounter",
                          "interactionType": "https://schema.org/LikeAction",
                          "userInteractionCount": 1
                        }
                      },
                      {
                        "@type": "Comment",
                        "text": "热门回复 B",
                        "interactionStatistic": {
                          "@type": "InteractionCounter",
                          "interactionType": "https://schema.org/LikeAction",
                          "userInteractionCount": 2
                        }
                      },
                      {
                        "@type": "Comment",
                        "text": "热门回复 C",
                        "interactionStatistic": {
                          "@type": "InteractionCounter",
                          "interactionType": "https://schema.org/LikeAction",
                          "userInteractionCount": 1
                        }
                      }
                    ],
                    "interactionStatistic": [
                      {
                        "@type": "InteractionCounter",
                        "interactionType": "https://schema.org/ViewAction",
                        "userInteractionCount": 5662
                      },
                      {
                        "@type": "InteractionCounter",
                        "interactionType": "https://schema.org/ReplyAction",
                        "userInteractionCount": 64
                      }
                    ]
                  }
                </script>
              </head>
              <body>
                <h1>现在没什么好用的 coding plan 了吗？</h1>
                <a href="/go/programmer">程序员</a>
                <small class="gray">
                  <a href="/member/hiboshi">hiboshi</a> ·
                  <span title="2026-06-29 09:11:16 +08:00">13h 28m ago</span> ·
                  5662 views
                </small>
                <div class="topic_content"><p>正文</p></div>
                <div class="cell">
                  <div class="fr">
                    <a href="/tag/Coding" class="tag"><li class="fa fa-tag"></li> Coding</a>
                    <a href="/tag/plan" class="tag"><li class="fa fa-tag"></li> plan</a>
                    <a href="/tag/glm" class="tag"><li class="fa fa-tag"></li> glm</a>
                  </div>
                  <span class="gray">64 replies</span>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1223541, html = html)

        assertThat(topic.viewCount).isEqualTo(5662)
        assertThat(topic.hotReplyCount).isEqualTo(3)
        assertThat(topic.tags).containsExactly("Coding", "plan", "glm").inOrder()
    }

    @Test
    fun parseTopicHtml_omitsSupplementalMetadataWhenMarkupIsMissing() {
        val html = """
            <html>
              <body>
                <h1>没有补充信息的主题</h1>
                <div class="topic_content"><p>正文</p></div>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1, html = html)

        assertThat(topic.viewCount).isNull()
        assertThat(topic.hotReplyCount).isNull()
        assertThat(topic.tags).isEmpty()
    }

    @Test
    fun parseTopicHtml_countsOnlyHeartRowsAsHotRepliesWhenJsonLdIsMissing() {
        val html = """
            <html>
              <body>
                <h1>使用 DOM 兜底解析热门回复</h1>
                <div class="topic_content"><p>正文</p></div>
                <span class="small fade">
                  <img src="/static/img/heart_20250818.png" alt="heart" /> 1
                </span>
                <span class="small fade">
                  <img src="/static/img/badge.png" alt="badge" /> badge
                </span>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1, html = html)

        assertThat(topic.hotReplyCount).isEqualTo(1)
    }
}
