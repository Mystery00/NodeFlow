package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exHtmlParserTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseUserRecentTopics_readsTopicCellsFromMemberPage() {
        val html = """
            <html>
              <body>
                <div class="box">
                  <div class="cell"><span class="gray">Livid's recent topics</span></div>
                  <div class="cell item">
                    <table><tr>
                      <td width="auto">
                        <span class="item_title"><a href="/t/1219772#reply5" class="topic-link" id="topic-link-1219772">测试主题标题</a></span>
                        <span class="topic_info"><a class="node" href="/go/wunder">Wunder</a> &nbsp;•&nbsp; <strong><a href="/member/Livid">Livid</a></strong> &nbsp;•&nbsp; <span title="2026-06-14 11:04:13 +08:00">Jun 14</span></span>
                      </td>
                      <td><a href="/t/1219772#reply5" class="count_livid">5</a></td>
                    </tr></table>
                  </div>
                  <div class="inner"><a href="/member/Livid/topics">More topics by Livid</a></div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topics = parser.parseUserRecentTopics(html)

        assertThat(topics).hasSize(1)
        assertThat(topics.single().id).isEqualTo(1219772)
        assertThat(topics.single().title).isEqualTo("测试主题标题")
        assertThat(topics.single().node.name).isEqualTo("wunder")
        assertThat(topics.single().replyCount).isEqualTo(5)
    }

    @Test
    fun parseUserRecentReplies_readsReplyRowsWithTargetTopic() {
        val html = """
            <html>
              <body>
                <div class="box">
                  <div class="cell"><span class="gray">Livid's recent replies</span></div>
                  <div class="dock_area">
                    <table><tr>
                      <td><div class="fr"><span class="fade" title="2026-07-11 19:04:44 +08:00">36 mins ago</span></div>
                      <span class="gray">Replied to a topic by <a href="/member/Livid">Livid</a> <span class="chevron">›</span> <a href="/go/wunder">Wunder</a> <span class="chevron">›</span> <a href="/t/1226562#reply4">目标主题标题</a></span></td>
                    </tr></table>
                  </div>
                  <div class="inner">
                    <div class="reply_content">这是一条回复内容</div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val replies = parser.parseUserRecentReplies(html)

        assertThat(replies).hasSize(1)
        assertThat(replies.single().topicId).isEqualTo(1226562)
        assertThat(replies.single().topicTitle).isEqualTo("目标主题标题")
        assertThat(replies.single().nodeName).isEqualTo("wunder")
        assertThat(replies.single().content).isEqualTo("这是一条回复内容")
        assertThat(replies.single().createdAtEpochSeconds)
            .isEqualTo(java.time.OffsetDateTime.parse("2026-07-11T19:04:44+08:00").toEpochSecond())
    }

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
        assertThat(status.canCheckIn).isFalse()
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

        val page = parser.parseDailyCheckInPage(html)

        assertThat(page).isNotNull()
        assertThat(page!!.checkIn.checkedIn).isFalse()
        assertThat(page.checkIn.canCheckIn).isTrue()
        assertThat(page.checkIn.continuousDays).isEqualTo(8)
        assertThat(page.redeemOnce).isEqualTo("84830")
    }

    @Test
    fun isDailyCheckInSuccess_requiresClaimedStructureAndPositiveMarker() {
        val html = """
            <html><body>
              <div class="cell">每日登录奖励已领取</div>
              <input type="button" onclick="location.href = '/balance';" value="查看我的账户余额" />
            </body></html>
        """.trimIndent()

        assertThat(parser.isDailyCheckInSuccess(html)).isTrue()
    }

    @Test
    fun isDailyCheckInSuccess_rejectsPageWhenRedeemButtonRemains() {
        val html = """
            <html><body>
              <div class="cell">领取成功</div>
              <input type="button" onclick="location.href = '/mission/daily/redeem?once=84830';" value="领取奖励" />
            </body></html>
        """.trimIndent()

        assertThat(parser.isDailyCheckInSuccess(html)).isFalse()
    }

    @Test
    fun hasDailyCheckInRiskNotice_recognizesCleanBrowserPrompt() {
        val html = """<html><body>请用一个干净安装的浏览器重试</body></html>"""

        assertThat(parser.hasDailyCheckInRiskNotice(html)).isTrue()
    }

    @Test
    fun hasDailyCheckInRiskNotice_recognizesCloudflareChallenge() {
        val html = """<html><head><title>Just a moment...</title></head><body><div id="cf-chl-widget"></div></body></html>"""

        assertThat(parser.hasDailyCheckInRiskNotice(html)).isTrue()
    }

    @Test
    fun hasAccessChallenge_ignoresMarkerInsideValidNotificationContent() {
        val html = """
            <div id="Main">
              <div class="cell" id="n_42">
                <a href="/member/alice"><img class="avatar" src="/avatar.png"></a>
                <span class="fade"><a href="/member/alice">alice</a> 回复了你
                  <a class="topic-link" href="/t/123#reply7">测试主题</a>
                </span>
                <span class="snow">1 小时前</span>
                <div class="payload">这里讨论的是 Cloudflare 配置</div>
              </div>
            </div>
        """.trimIndent()

        assertThat(parser.hasAccessChallenge(html)).isFalse()
    }

    @Test
    fun parseLatestDailyReward_readsRewardCellAfterDescription() {
        val html = """
            <html><body><table>
              <tr><td>2026-07-15</td><td>每日登录奖励</td><td>+12</td><td>0</td><td>记录</td></tr>
              <tr><td>2026-07-14</td><td>每日登录奖励</td><td>+8</td><td>0</td><td>记录</td></tr>
            </table></body></html>
        """.trimIndent()

        assertThat(parser.parseLatestDailyReward(html)).isEqualTo(12)
    }

    @Test
    fun parseLatestDailyReward_ignoresNonPositiveOrUnrelatedRows() {
        val html = """
            <html><body><table>
              <tr><td>2026-07-15</td><td>主题回复</td><td>+20</td><td>0</td><td>记录</td></tr>
              <tr><td>2026-07-14</td><td>每日登录奖励</td><td>0</td><td>0</td><td>记录</td></tr>
            </table></body></html>
        """.trimIndent()

        assertThat(parser.parseLatestDailyReward(html)).isNull()
    }

    @Test
    fun parseNotifications_readsReplyContentTopicTimeAndReferenceLocator() {
        val html = """
            <html><body><div id="Main">
              <div class="cell" id="n_12345">
                <table><tr>
                  <td><a href="/member/replier"><img class="avatar" src="//cdn.v2ex.com/avatar.png" /></a></td>
                  <td>
                    <span class="fade"><a href="/member/replier"><strong>replier</strong></a> 回复了你在主题 › <a class="topic-link" href="/t/67890#reply5">测试主题</a> 里的回复</span>
                    <span class="snow">2 小时前</span>
                    <div class="payload"><a href="/member/original">@original</a> #3<br />回复正文</div>
                  </td>
                </tr></table>
              </div>
            </div></body></html>
        """.trimIndent()

        val notifications = parser.parseNotifications(html)

        assertThat(notifications).hasSize(1)
        val notification = notifications.single()
        assertThat(notification.id).isEqualTo(12345)
        assertThat(notification.actor.username).isEqualTo("replier")
        assertThat(notification.actor.avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar.png")
        assertThat(notification.action).isEqualTo("回复了你在主题里的回复")
        assertThat(notification.topicId).isEqualTo(67890)
        assertThat(notification.topicTitle).isEqualTo("测试主题")
        assertThat(notification.replyFloor).isEqualTo(5)
        assertThat(notification.relativeTime).isEqualTo("2 小时前")
        assertThat(notification.contentRendered).contains("回复正文")
        assertThat(notification.referenceLocator?.username).isEqualTo("original")
        assertThat(notification.referenceLocator?.floor).isEqualTo(3)
    }

    @Test
    fun parseNotifications_keepsThankNotificationWithoutPayload() {
        val html = """
            <html><body><div id="Main">
              <div class="cell" id="n_7"><table><tr>
                <td><a href="/member/helper"><img class="avatar" src="/avatar.png" /></a></td>
                <td>
                  <span class="fade"><a href="/member/helper">helper</a> 感谢了你在主题 › <a class="topic-link" href="/t/99#reply2">感谢主题</a> 里的回复</span>
                  <span class="snow">12 天前</span>
                </td>
              </tr></table></div>
            </div></body></html>
        """.trimIndent()

        val notification = parser.parseNotifications(html).single()

        assertThat(notification.action).isEqualTo("感谢了你在主题里的回复")
        assertThat(notification.contentRendered).isNull()
        assertThat(notification.referenceLocator).isNull()
    }

    @Test
    fun parseNotifications_skipsCellWithoutTopicLink() {
        val html = """
            <html><body><div id="Main">
              <div class="cell" id="n_8"><span class="fade">未知提醒</span></div>
            </div></body></html>
        """.trimIndent()

        assertThat(parser.parseNotifications(html)).isEmpty()
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
    fun parseNodeDetail_readsNodeIconFromJsonLd() {
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
                    "mainEntity": {
                      "@type": "ItemList",
                      "numberOfItems": 12887
                    }
                  }
                </script>
              </head>
              <body>
                <h1>Android</h1>
              </body>
            </html>
        """.trimIndent()

        val node = parser.parseNodeDetail("android", html)

        assertThat(node).isNotNull()
        assertThat(node!!.name).isEqualTo("android")
        assertThat(node.title).isEqualTo("Android")
        assertThat(node.header).isEqualTo("来自 Google 的开放源代码智能手机平台。")
        assertThat(node.avatarUrl).isEqualTo("https://cdn.v2ex.com/navatar/d67d/8ab4/39_xxxlarge.png?m=1754172750")
        assertThat(node.topics).isEqualTo(12887)
    }

    @Test
    fun parseNodeDetail_returnsNullWhenNodeMarkupIsMissing() {
        val node = parser.parseNodeDetail("android", "<html><body>empty</body></html>")

        assertThat(node).isNull()
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
    fun parseTopicHtml_prefersMainTopicContentOverNodeNotice() {
        val html = """
            <html>
              <body>
                <div class="topic_content">
                  <p>这个节点的存在，只是为了将一类信息进行归类。</p>
                </div>
                <div id="Main">
                  <div class="box">
                    <div class="header">
                      <a href="/go/flamewar">水深火热</a>
                      <h1>受限归档主题</h1>
                      <small class="gray">
                        <a href="/member/alice">alice</a>
                      </small>
                    </div>
                    <div class="cell">
                      <div class="topic_content">
                        <p>真实主题正文</p>
                      </div>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1221181, html = html)

        assertThat(topic).isNotNull()
        assertThat(topic!!.contentRendered).contains("真实主题正文")
        assertThat(topic.contentRendered).doesNotContain("这个节点的存在")
    }

    @Test
    fun parseTopicHtml_ignoresNodeSidebarNoticeWhenTopicContentIsMissing() {
        val html = """
            <html>
              <body>
                <div id="Main">
                  <div class="box">
                    <div class="header">
                      <a href="/go/flamewar">水深火热</a>
                      <h1>受限归档主题</h1>
                      <small class="gray"><a href="/member/alice">alice</a></small>
                    </div>
                    <div class="topic_buttons">主题操作</div>
                  </div>
                  <div class="box">
                    <div id="r_17800001" class="cell">
                      <strong><a href="/member/bob">bob</a></strong>
                      <span class="no">1</span>
                      <div class="reply_content">可见回复</div>
                    </div>
                  </div>
                </div>
                <div id="Rightbar">
                  <div id="node_sidebar">
                    <div class="topic_content markdown_body">
                      <p>这个节点的存在，只是为了将一类信息进行归类。</p>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1221181, html = html)

        assertThat(topic).isNotNull()
        assertThat(topic!!.title).isEqualTo("受限归档主题")
        assertThat(topic.contentRendered).isEmpty()
        assertThat(topic.contentRendered).doesNotContain("这个节点的存在")
        assertThat(topic.replies).hasSize(1)
        assertThat(topic.replies.single().contentRendered).isEqualTo("可见回复")
    }

    @Test
    fun parseTopicHtml_returnsNullForRestrictedSignInPageWithTopicContent() {
        val html = """
            <html>
              <body>
                <div id="problem" class="topic_content">需要登录后访问</div>
                <form action="/signin" method="post">
                  <input type="hidden" name="next" value="/restricted" />
                  <input type="password" name="password" />
                </form>
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.parseTopicHtml(topicId = 1221181, html = html)).isNull()
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

        assertThat(topic).isNotNull()
        assertThat(topic!!.viewCount).isEqualTo(5662)
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

        assertThat(topic).isNotNull()
        assertThat(topic!!.viewCount).isNull()
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

        assertThat(topic!!.hotReplyCount).isEqualTo(1)
    }

    @Test
    fun parseTopicHtml_readsRepliesAndPaginationFromTopicPage() {
        val html = """
            <html>
              <head>
                <meta property="article:section" content="Android" />
              </head>
              <body>
                <div id="Wrapper">
                  <div class="header">
                    <div class="fr">
                      <a href="/member/adz2k"><img src="//cdn.v2ex.com/gravatar/abc.png" class="avatar" alt="adz2k" /></a>
                    </div>
                    <div><a href="/">V2EX</a> <span class="chevron">›</span> <a href="/go/android">Android</a></div>
                    <h1>港版安卓机是满血的国际版安卓机吗？</h1>
                    <small class="gray"><a href="/member/adz2k">adz2k</a> · <span title="2026-07-10 16:33:39 +08:00">20h 5m ago</span> · 1656 views</small>
                  </div>
                  <div class="cell"><div class="topic_content"><p>正文段落</p></div></div>
                  <div id="r_17853599" class="cell">
                    <table>
                      <tr>
                        <td><img src="//cdn.v2ex.com/avatar/x_normal.png" class="avatar" alt="xingfu0539" /></td>
                        <td>
                          <div class="fr"> &nbsp; <span class="no">1</span></div>
                          <strong><a href="/member/xingfu0539" class="dark">xingfu0539</a></strong>
                          <span class="ago" title="2026-07-10 19:37:31 +08:00">17h 1m ago</span>
                          <div class="reply_content">第一条回复</div>
                        </td>
                      </tr>
                    </table>
                  </div>
                  <div id="r_17853600" class="cell">
                    <table>
                      <tr>
                        <td><img src="//cdn.v2ex.com/avatar/y_normal.png" class="avatar" alt="moefishtang" /></td>
                        <td>
                          <div class="fr">
                            <span class="small fade"><img src="/static/img/heart_neue@2x.png" alt="❤️" /> 3</span>
                            &nbsp; <span class="no">2</span>
                          </div>
                          <strong><a href="/member/moefishtang" class="dark">moefishtang</a></strong>
                          <span class="ago" title="2026-07-10 19:43:00 +08:00">17h ago</span>
                          <div class="reply_content">回应 <a href="/member/xingfu0539">@xingfu0539</a> 的内容</div>
                        </td>
                      </tr>
                    </table>
                  </div>
                  <input type="number" class="page_input" min="1" max="3" value="1" />
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1226421, html = html)

        assertThat(topic).isNotNull()
        assertThat(topic!!.title).isEqualTo("港版安卓机是满血的国际版安卓机吗？")
        assertThat(topic.authorName).isEqualTo("adz2k")
        assertThat(topic.authorAvatarUrl).isEqualTo("https://cdn.v2ex.com/gravatar/abc.png")
        assertThat(topic.nodeName).isEqualTo("android")
        assertThat(topic.nodeTitle).isEqualTo("Android")
        assertThat(topic.createdAtEpochSeconds)
            .isEqualTo(java.time.OffsetDateTime.parse("2026-07-10T16:33:39+08:00").toEpochSecond())
        assertThat(topic.contentRendered).contains("正文段落")
        assertThat(topic.viewCount).isEqualTo(1656)
        assertThat(topic.pageCount).isEqualTo(3)
        assertThat(topic.replies).hasSize(2)

        val first = topic.replies[0]
        assertThat(first.id).isEqualTo(17853599)
        assertThat(first.floor).isEqualTo(1)
        assertThat(first.author.username).isEqualTo("xingfu0539")
        assertThat(first.author.avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar/x_normal.png")
        assertThat(first.contentRendered).isEqualTo("第一条回复")
        assertThat(first.createdAtEpochSeconds)
            .isEqualTo(java.time.OffsetDateTime.parse("2026-07-10T19:37:31+08:00").toEpochSecond())
        assertThat(first.thanks).isEqualTo(0)

        val second = topic.replies[1]
        assertThat(second.id).isEqualTo(17853600)
        assertThat(second.floor).isEqualTo(2)
        assertThat(second.author.username).isEqualTo("moefishtang")
        assertThat(second.thanks).isEqualTo(3)
        assertThat(second.contentRendered).contains("@xingfu0539")
    }

    @Test
    fun parseTopicHtml_returnsNullForNonTopicPage() {
        val html = """
            <html>
              <body>
                <div class="box">
                  <div class="header">登录 V2EX</div>
                  <form action="/signin" method="post"><input type="text" name="u" /></form>
                </div>
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.parseTopicHtml(topicId = 1, html = html)).isNull()
    }

    @Test
    fun parseTopicHtml_readsTitleOnlyTopicWithoutReplies() {
        val html = """
            <html>
              <body>
                <div class="header">
                  <a href="/go/programmer">程序员</a>
                  <h1>只有标题的主题</h1>
                  <small class="gray"><a href="/member/author">author</a></small>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1228198, html = html)

        assertThat(topic).isNotNull()
        assertThat(topic!!.title).isEqualTo("只有标题的主题")
        assertThat(topic.contentRendered).isEmpty()
        assertThat(topic.replies).isEmpty()
    }

    @Test
    fun parseTopicHtml_usesExpandedImageUrlAsReplyPlainText() {
        val html = """
            <html><body><div id="Wrapper">
              <div class="header">
                <div><a href="/">V2EX</a><a href="/go/android">Android</a></div>
                <h1>图片回复</h1>
                <small><a href="/member/author">author</a></small>
              </div>
              <div class="cell"><div class="topic_content">正文</div></div>
              <div id="r_100" class="cell"><table><tr><td>
                <div class="fr"><span class="no">1</span></div>
                <strong><a href="/member/tester">tester</a></strong>
                <div class="reply_content">
                  回复内容<br>
                  <a href="https://i.v2ex.co/example.png"><img src="https://i.v2ex.co/example_thumbnail.png"></a>
                </div>
              </td></tr></table></div>
            </div></body></html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 42, html = html)

        assertThat(topic).isNotNull()
        assertThat(topic!!.replies.single().content).contains("https://i.v2ex.co/example.png")
        assertThat(topic.replies.single().content).doesNotContain("example_thumbnail.png")
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

    @Test
    fun parsePolishNoteId_findsNoteByPrefix() {
        val html = """
            <div class="box">
              <div class="inner"><span class="bigger"><a href="/notes/11" class="black">普通笔记</a></span></div>
              <div class="inner"><span class="bigger"><a href="/notes/67331" class="black">V2EX_Polish_settings{&#34;settings-sync&#34;:{&#34;version&#34;:46（截断</a></span></div>
            </div>
        """.trimIndent()

        assertThat(parser.parsePolishNoteId(html)).isEqualTo(67331L)
    }

    @Test
    fun parsePolishNoteId_returnsNullWhenAbsent() {
        val html = """<div><a href="/notes/11" class="black">普通笔记</a></div>"""
        assertThat(parser.parsePolishNoteId(html)).isNull()
        assertThat(parser.parsePolishNoteId("<html><body>空页面</body></html>")).isNull()
    }

    @Test
    fun parseNoteEditContent_extractsTextareaWithEntitiesDecoded() {
        val html = """
            <form><textarea class="mle" name="content">V2EX_Polish_settings{&#34;member-tag&#34;:{}}</textarea></form>
        """.trimIndent()

        assertThat(parser.parseNoteEditContent(html))
            .isEqualTo("""V2EX_Polish_settings{"member-tag":{}}""")
    }

    @Test
    fun parseNoteEditContent_returnsNullWithoutTextarea() {
        assertThat(parser.parseNoteEditContent("<html><body>无</body></html>")).isNull()
    }

    @Test
    fun parseReplyForm_readsDynamicFields() {
        val parsed = parser.parseReplyForm(
            topicId = 42,
            html = """
                <html><body>
                  <form method="post" action="/t/42">
                    <textarea id="reply_content" name="content" maxlength="10000"></textarea>
                    <input type="hidden" name="once" value="redacted" />
                    <input type="hidden" name="return_to_page" value="3" />
                  </form>
                </body></html>
            """.trimIndent(),
        )

        assertThat(parsed?.actionUrl).isEqualTo("https://www.v2ex.com/t/42")
        assertThat(parsed?.contentField).isEqualTo("content")
        assertThat(parsed?.maxLength).isEqualTo(10_000)
        assertThat(parsed?.hiddenFields).containsExactly(
            "once",
            "redacted",
            "return_to_page",
            "3",
        )
    }

    @Test
    fun parseReplyForm_rejectsOtherTopicAndSignInPage() {
        val otherTopic = """
            <form method="post" action="/t/43">
              <textarea name="content"></textarea>
            </form>
        """.trimIndent()
        val signIn = """
            <form method="post" action="/signin">
              <input type="password" name="password" />
              <textarea name="content"></textarea>
            </form>
        """.trimIndent()

        assertThat(parser.parseReplyForm(42, otherTopic)).isNull()
        assertThat(parser.parseReplyForm(42, signIn)).isNull()
    }

    @Test
    fun parseImageUploadResponse_acceptsStringSuccessAndNormalizesUrl() {
        val parsed = parser.parseImageUploadResponse(
            """{"success":"true","name":"sample","uri":"sample.png","url_o":"//i.v2ex.co/sample.png","url_b":"//i.v2ex.co/sampleb.png"}""",
        )

        assertThat(parsed?.imageId).isEqualTo("sample")
        assertThat(parsed?.originalUrl).isEqualTo("https://i.v2ex.co/sample.png")
        assertThat(parsed?.detailUrl).isEqualTo("https://www.v2ex.com/i/sample.png")
    }

    @Test
    fun parseImageUploadResponse_rejectsFailureAndUnexpectedHost() {
        assertThat(parser.parseImageUploadResponse("""{"success":"false","message":"quota"}"""))
            .isNull()
        assertThat(
            parser.parseImageUploadResponse(
                """{"success":true,"name":"sample","uri":"sample.png","url_o":"//example.com/sample.png"}""",
            ),
        ).isNull()
    }

    @Test
    fun parseImageUploadPage_classifiesAvailableLoginAndDeniedPages() {
        assertThat(
            parser.parseImageUploadPage(
                """<form action="/i/upload" method="post"><input type="file" name="qqfile" /></form>""",
            ),
        ).isEqualTo(V2exHtmlParser.ParsedImageUploadPage.Available)
        assertThat(parser.parseImageUploadPage("""<a href="/signin">登录</a>"""))
            .isEqualTo(V2exHtmlParser.ParsedImageUploadPage.AuthenticationRequired)
        assertThat(parser.parseImageUploadPage("""<a href="/i/about">图库介绍</a>"""))
            .isEqualTo(V2exHtmlParser.ParsedImageUploadPage.PermissionDenied)
    }

    @Test
    fun parseV2exProblem_readsStructuredMessage() {
        assertThat(parser.parseV2exProblem("""<div class="problem">请不要频繁回复</div>"""))
            .isEqualTo("请不要频繁回复")
    }

    @Test
    fun parseTopicHtml_readsTotalReplyCountFromHeader() {
        val html = """
            <html>
              <body>
                <h1>分页主题</h1>
                <div class="topic_content">正文</div>
                <div class="cell"><span class="gray">342 条回复 &nbsp;•&nbsp; 到目前为止</span></div>
                <div id="r_1">
                  <span class="no">1</span>
                  <strong><a href="/member/alice">alice</a></strong>
                  <div class="reply_content">第一条回复</div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html))

        assertThat(topic.replyCount).isEqualTo(342)
    }

    @Test
    fun parseTopicHtml_replyCountIsNullWhenHeaderMissing() {
        val html = """
            <html>
              <body>
                <h1>无回复主题</h1>
                <div class="topic_content">正文</div>
              </body>
            </html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html))

        assertThat(topic.replyCount).isNull()
    }

    @Test
    fun parseTopicHtml_fallbackFloorUsesFloorOffset() {
        // span.no 缺失时，第 2 页的兜底楼层应从偏移量继续，而不是从 1 重新开始
        val html = """
            <html>
              <body>
                <h1>分页主题</h1>
                <div id="r_201">
                  <strong><a href="/member/alice">alice</a></strong>
                  <div class="reply_content">第二页第一条</div>
                </div>
                <div id="r_202">
                  <strong><a href="/member/bob">bob</a></strong>
                  <div class="reply_content">第二页第二条</div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html, floorOffset = 100))

        assertThat(topic.replies.map { it.floor }).containsExactly(101, 102).inOrder()
    }

    @Test
    fun parseTopicHtml_explicitFloorIgnoresFloorOffset() {
        val html = """
            <html>
              <body>
                <h1>分页主题</h1>
                <div id="r_201">
                  <span class="no">150</span>
                  <strong><a href="/member/alice">alice</a></strong>
                  <div class="reply_content">显式楼层</div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html, floorOffset = 100))

        assertThat(topic.replies.single().floor).isEqualTo(150)
    }

    @Test
    fun parseTopicHtml_parsesSubtleAppends() {
        val html = """
            <html>
              <body>
                <div id="Main">
                  <div class="box">
                    <div class="header">
                      <h1>带附言的主题</h1>
                      <small class="gray"><a href="/member/alice">alice</a></small>
                    </div>
                    <div class="topic_content">正文</div>
                    <div class="subtle">
                      <span class="fade">第 1 条附言 &nbsp;•&nbsp; <span title="2026-07-24 15:30:00 +08:00">3 小时前</span></span>
                      <div class="sep5"></div>
                      <div class="topic_content">第一条附言内容</div>
                    </div>
                    <div class="subtle">
                      <span class="fade">第 2 条附言 &nbsp;•&nbsp; 1 小时前</span>
                      <div class="sep5"></div>
                      <div class="topic_content">第二条附言内容</div>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(topicId = 1228444L, html = html))

        assertThat(topic.appends).hasSize(2)
        assertThat(topic.appends[0].index).isEqualTo(1)
        assertThat(topic.appends[0].contentRendered).isEqualTo("第一条附言内容")
        assertThat(topic.appends[0].createdAtEpochSeconds)
            .isEqualTo(java.time.OffsetDateTime.parse("2026-07-24T15:30:00+08:00").toEpochSecond())

        assertThat(topic.appends[1].index).isEqualTo(2)
        assertThat(topic.appends[1].contentRendered).isEqualTo("第二条附言内容")
        assertThat(topic.appends[1].relativeTime).isEqualTo("第 2 条附言 • 1 小时前")
    }

    @Test
    fun parseTopicHtml_parsesEnglishSupplementSubtle() {
        val html = """
            <html>
              <body>
                <div id="Main">
                  <div class="box">
                    <div class="header">
                      <h1>cursor 不支持支付宝支付订阅，还有其他方式？</h1>
                      <small class="gray"><a href="/member/wangt981">wangt981</a></small>
                    </div>
                    <div class="cell">
                      <div class="topic_content">如题，还有什么办法可以订阅！</div>
                    </div>
                    <div class="subtle">
                      <span class="fade">Supplement 1 &nbsp;·&nbsp; <span title="2026-07-24 15:08:59 +08:00">6h 47m ago</span></span>
                      <div class="sep5"></div>
                      <div class="topic_content">感谢各位大佬建议，目前切换到新加坡代理可以，美国、日本、澳大利亚都不行！！</div>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(topicId = 1229541L, html = html))

        assertThat(topic.appends).hasSize(1)
        assertThat(topic.appends[0].index).isEqualTo(1)
        assertThat(topic.appends[0].contentRendered).isEqualTo("感谢各位大佬建议，目前切换到新加坡代理可以，美国、日本、澳大利亚都不行！！")
        assertThat(topic.appends[0].createdAtEpochSeconds)
            .isEqualTo(java.time.OffsetDateTime.parse("2026-07-24T15:08:59+08:00").toEpochSecond())
    }
}
