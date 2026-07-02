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
    fun parseUnreadNotificationCount_readsHomeUnreadButton() {
        val html = """
            <html>
              <body>
                <a href="/signout?once=12345">Sign Out</a>
                <input type="button" class="super special button" value="7 条未读提醒" />
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.parseUnreadNotificationCount(html)).isEqualTo(7)
    }

    @Test
    fun parseUnreadNotificationCount_returnsZeroWhenLoggedInHomeHasNoUnreadButton() {
        val html = """
            <html>
              <body>
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <a href="/signout?once=12345">Sign Out</a>
                </div>
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.parseUnreadNotificationCount(html)).isEqualTo(0)
    }

    @Test
    fun hasSignInEntry_returnsTrueForSignInLinkAndForm() {
        val linkHtml = """<a href="/signin">Sign In</a>"""
        val formHtml = """<form action="/signin"><input name="once" /></form>"""

        assertThat(parser.hasSignInEntry(linkHtml)).isTrue()
        assertThat(parser.hasSignInEntry(formHtml)).isTrue()
    }

    @Test
    fun hasSignInEntry_returnsFalseWhenSignOutExists() {
        val html = """
            <html>
              <body>
                <a href="/signin">Sign In</a>
                <a href="/signout?once=12345">Sign Out</a>
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.hasSignInEntry(html)).isFalse()
    }

    @Test
    fun parseDailyCheckIn_readsCheckedInStatusAndContinuousDays() {
        val html = """
            <html>
              <body>
                <div class="cell">
                  <span>currentUser 已连续签到 12 天</span>
                  <input type="button" onclick="location.href = '/balance';" value="已签到" />
                </div>
              </body>
            </html>
        """.trimIndent()

        val status = parser.parseDailyCheckIn(html)

        assertThat(status).isNotNull()
        assertThat(status!!.checkedIn).isTrue()
        assertThat(status.continuousDays).isEqualTo(12)
        assertThat(status.redeemOnce).isNull()
    }

    @Test
    fun parseDailyCheckIn_readsRedeemOnceWhenCheckInAvailable() {
        val html = """
            <html>
              <body>
                <div class="cell">
                  <span>您已连续登录 8 天</span>
                  <input type="button" onclick="location.href = '/mission/daily/redeem?once=84830';" value="领取每日登录奖励" />
                </div>
              </body>
            </html>
        """.trimIndent()

        val status = parser.parseDailyCheckIn(html)

        assertThat(status).isNotNull()
        assertThat(status!!.checkedIn).isFalse()
        assertThat(status.continuousDays).isEqualTo(8)
        assertThat(status.redeemOnce).isEqualTo("84830")
    }

    @Test
    fun parseAccountWealth_readsCurrencyCountsFromBalancePage() {
        val html = """
            <html>
              <body>
                <div class="box">
                  <table>
                    <tr><td>金币</td><td><strong>1</strong></td></tr>
                    <tr><td>银币</td><td><strong>23</strong></td></tr>
                    <tr><td>铜币</td><td><strong>4,567</strong></td></tr>
                  </table>
                </div>
              </body>
            </html>
        """.trimIndent()

        val wealth = parser.parseAccountWealth(html)

        assertThat(wealth).isNotNull()
        assertThat(wealth!!.gold).isEqualTo(1)
        assertThat(wealth.silver).isEqualTo(23)
        assertThat(wealth.bronze).isEqualTo(4567)
    }

    @Test
    fun parseAccountWealth_readsCurrencyCountsFromCombinedIconRow() {
        val html = """
            <html>
              <body>
                <div class="box">
                  <div class="cell">
                    <img src="/static/img/gold.png" alt="gold" /> 1
                    <img src="/static/img/silver.png" alt="silver" /> 23
                    <img src="/static/img/bronze.png" alt="bronze" /> 4,567
                  </div>
                  <div class="cell">
                    2026-06-30 每日登录奖励 铜币 10
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val wealth = parser.parseAccountWealth(html)

        assertThat(wealth).isNotNull()
        assertThat(wealth!!.gold).isEqualTo(1)
        assertThat(wealth.silver).isEqualTo(23)
        assertThat(wealth.bronze).isEqualTo(4567)
    }

    @Test
    fun parseAccountWealth_readsCurrencyCountsBeforeIcons() {
        val html = """
            <html>
              <body>
                <div class="box">
                  <div class="cell">
                    6 <img src="/static/img/gold.png" alt="gold" />
                    28 <img src="/static/img/silver.png" alt="silver" />
                    62 <img src="/static/img/bronze.png" alt="bronze" />
                  </div>
                  <div class="cell">
                    2026-06-30 每日登录奖励 铜币 10
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val wealth = parser.parseAccountWealth(html)

        assertThat(wealth).isNotNull()
        assertThat(wealth!!.gold).isEqualTo(6)
        assertThat(wealth.silver).isEqualTo(28)
        assertThat(wealth.bronze).isEqualTo(62)
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
    fun parseTopicList_readsNodeFromRecentPageItem() {
        val html = """
            <html>
              <body>
                <div class="cell item">
                  <table>
                    <tr>
                      <td>
                        <a href="/member/jimmyczm">
                          <img src="//cdn.v2ex.com/avatar/recent_normal.png" class="avatar" alt="jimmyczm" />
                        </a>
                      </td>
                      <td>
                        <span class="item_title">
                          <a href="/t/1224500#reply18" class="topic-link" id="topic-link-1224500">Recent topic</a>
                        </span>
                        <div class="sep5"></div>
                        <span class="topic_info">
                          <div class="votes"></div>
                          <a class="node" href="/go/bb">Broadband</a>
                          &nbsp;•&nbsp;
                          <strong><a href="/member/jimmyczm">jimmyczm</a></strong>
                          &nbsp;•&nbsp;
                          <span title="2026-07-02 20:38:20 +08:00">1h 28m ago</span>
                        </span>
                      </td>
                      <td><a href="/t/1224500#reply18" class="count_livid">18</a></td>
                    </tr>
                  </table>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topics = parser.parseTopicList(html)

        assertThat(topics).hasSize(1)
        assertThat(topics.first().node.name).isEqualTo("bb")
        assertThat(topics.first().node.title).isEqualTo("Broadband")
        assertThat(topics.first().author.username).isEqualTo("jimmyczm")
        assertThat(topics.first().lastTouchedAtEpochSeconds).isEqualTo(1782995900)
    }

    @Test
    fun parseTopicList_readsNodeAndAuthorFromMobileRecentPageItem() {
        val html = """
            <html>
              <body>
                <div class="cell item">
                  <table>
                    <tr>
                      <td>
                        <a href="/member/geniushui">
                          <img src="//cdn.v2ex.com/avatar/mobile_normal.png" class="avatar" alt="geniushui" />
                        </a>
                      </td>
                      <td>
                        <span class="small fade">
                          <a class="node" href="/go/create">Create</a>
                          &nbsp;•&nbsp;
                          <strong><a href="/member/geniushui">geniushui</a></strong>
                        </span>
                        <div class="sep5"></div>
                        <span class="item_title">
                          <a href="/t/1224603#reply0" class="topic-link" id="topic-link-1224603">Mobile recent topic</a>
                        </span>
                        <div class="sep5"></div>
                        <span class="small fade">1h 54m ago</span>
                      </td>
                    </tr>
                  </table>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topics = parser.parseTopicList(html)

        assertThat(topics).hasSize(1)
        assertThat(topics.first().node.name).isEqualTo("create")
        assertThat(topics.first().node.title).isEqualTo("Create")
        assertThat(topics.first().author.username).isEqualTo("geniushui")
        assertThat(topics.first().avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar/mobile_normal.png")
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

    @Test
    fun parseUserProfile_readsMemberNumberAndDailyActivityRank() {
        val html = """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "@context": "https://schema.org",
                    "@type": "ProfilePage",
                    "mainEntity": {
                      "@type": "Person",
                      "name": "Mystery0",
                      "identifier": "243339"
                    }
                  }
                </script>
              </head>
              <body>
                <div id="Main">
                  <div class="box">
                    <div class="cell">
                      <img class="avatar" data-uid="243339" src="//cdn.v2ex.com/avatar/sample_large.png" />
                      <span class="gray">
                        V2EX member #243339, joined on 2017-07-20 22:43:23 +08:00
                        <div class="sep5"></div>
                        Today's activity rank <a href="/top/dau">6,695</a>
                      </span>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val user = parser.parseUserProfile("Mystery0", html)

        assertThat(user.memberNumber).isEqualTo(243339)
        assertThat(user.dailyActivityRank).isEqualTo(6695)
    }
}
