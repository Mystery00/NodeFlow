package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.OffsetDateTime

/** 认证、签到、账户资产、活跃度、个人资料与 Polish 记事解析测试。 */
class V2exHtmlParserAuthAccountTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseSignInChallenge_readsDynamicFieldsAndMissingFormReturnsNull() {
        val enHtml = """
            <html><body>
              <form method="post" action="/signin">
                <input type="text" class="sl" name="user_field_hash" value="" placeholder="Username or Email" />
                <input type="password" class="sl" name="pass_field_hash" value="" />
                <img id="captcha-image" width="320" height="80" src="/_captcha" alt="CAPTCHA">
                <input type="text" class="sl" name="captcha_field_hash" value="" placeholder="Enter the code above, click to change">
                <input type="hidden" value="66994" name="once" />
                <input type="hidden" value="/" name="next" />
              </form>
            </body></html>
        """.trimIndent()
        val cnHtml = """
            <html><body>
              <form method="post" action="/signin">
                <input type="hidden" name="next" value="/mission/daily" />
                <input type="text" class="sl" name="user_hash" value="" />
                <input type="hidden" value="91811" name="once" />
                <input type="password" class="sl" name="pass_hash" value="" />
                <img id="captcha-image" width="280" height="80" src="/_captcha" alt="CAPTCHA">
                <input type="text" class="sl" name="captcha_hash" value="" placeholder="请输入上图中的验证码" />
              </form>
            </body></html>
        """.trimIndent()

        val en = requireNotNull(parser.parseSignInChallenge(enHtml))
        assertThat(en.usernameField).isEqualTo("user_field_hash")
        assertThat(en.passwordField).isEqualTo("pass_field_hash")
        assertThat(en.captchaField).isEqualTo("captcha_field_hash")
        assertThat(en.once).isEqualTo("66994")
        assertThat(en.next).isEqualTo("/")
        assertThat(en.captchaPath).isEqualTo("/_captcha")

        val cn = requireNotNull(parser.parseSignInChallenge(cnHtml))
        assertThat(cn.usernameField).isEqualTo("user_hash")
        assertThat(cn.passwordField).isEqualTo("pass_hash")
        assertThat(cn.captchaField).isEqualTo("captcha_hash")
        assertThat(cn.once).isEqualTo("91811")
        assertThat(cn.next).isEqualTo("/mission/daily")

        assertThat(parser.parseSignInChallenge("<html><body>No login form</body></html>")).isNull()
    }

    @Test
    fun parseLoginAccountAndTwoFactorChallenge() {
        val dailyHtml = """
            <html><body>
              <div id="Rightbar">
                <a href="/member/currentUser">currentUser</a>
                <img src="//cdn.v2ex.com/avatar/current_normal.png" />
              </div>
              <div class="cell">
                <input type="button" onclick="location.href = '/mission/daily/redeem?once=12345';" />
              </div>
            </body></html>
        """.trimIndent()
        val twoFactorHtml = """
            <html><body>
              <form method="post" action="/2fa?next=/mission/daily">
                <table>
                  <tr><td>两步验证</td></tr>
                  <tr><td><input type="hidden" name="once" value="24680" /></td></tr>
                </table>
              </form>
            </body></html>
        """.trimIndent()

        val account = requireNotNull(parser.parseLoginAccount(dailyHtml))
        assertThat(account.username).isEqualTo("currentUser")
        assertThat(account.avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar/current_large.png")

        val twoFactor = requireNotNull(parser.parseTwoFactorChallenge(twoFactorHtml))
        assertThat(twoFactor.once).isEqualTo("24680")
    }

    @Test
    fun hasSignInEntry_distinguishesSignInAndDesktopOrMobileSignOutControls() {
        assertThat(parser.hasSignInEntry("""<div id="Top"><a href="/signin">登录</a></div>""")).isTrue()
        assertThat(parser.hasSignInEntry("""<form action="/signin" method="post"></form>""")).isTrue()
        // 已登录页同时出现帖子/回复中提到的 /signin 链接时，不应误判为未登录
        assertThat(
            parser.hasSignInEntry(
                """
                <div id="Top"><a href="/signout?once=123">登出</a></div>
                <div class="topic_content"><a href="/signin">引用的登录链接</a></div>
                """.trimIndent(),
            ),
        ).isFalse()
        // V2EX 桌面版顶部登出控件使用 onclick="location.href = '/signout?once=...'"
        assertThat(
            parser.hasSignInEntry(
                """
                <div id="Top"><a href="#;" onclick="if (confirm('确定要从 V2EX 登出？')) { location.href = '/signout?once=98765'; }">登出</a></div>
                <div class="reply_content"><a href="/signin">signin</a></div>
                """.trimIndent(),
            ),
        ).isFalse()
    }

    @Test
    fun parseUnreadNotificationCount_readsUnreadButtonAndImplicitZeroWithDesktopSignOut() {
        val withUnread = """
            <html><body>
              <div id="Top"><a href="/signout?once=123">登出</a></div>
              <input type="button" class="super special button" onclick="location.href='/notifications';" value="3 条未读提醒" />
            </body></html>
        """.trimIndent()
        val desktopZero = """
            <html><body>
              <div id="Top"><a href="#;" onclick="if (confirm('确定要从 V2EX 登出？')) { location.href = '/signout?once=98765'; }">登出</a></div>
              <div id="Main"><div class="cell"><a href="/notifications">0 条未读提醒</a></div></div>
            </body></html>
        """.trimIndent()
        val desktopImplicitZero = """
            <html><body>
              <div id="Top"><a href="#;" onclick="if (confirm('确定要从 V2EX 登出？')) { location.href = '/signout?once=98765'; }">登出</a></div>
              <div id="Main"><div class="cell">普通首页内容</div></div>
            </body></html>
        """.trimIndent()

        assertThat(parser.parseUnreadNotificationCount(withUnread)).isEqualTo(3)
        assertThat(parser.parseUnreadNotificationCount(desktopZero)).isEqualTo(0)
        assertThat(parser.parseUnreadNotificationCount(desktopImplicitZero)).isEqualTo(0)
    }

    @Test
    fun parseDailyCheckIn_readsStatusRedeemAndOutcomeMarkers() {
        val checkedHtml = """
            <html><body>
              <div class="cell">每日登录奖励已领取</div>
              <div class="cell">已连续登录 12 天</div>
              <input type="button" onclick="location.href = '/balance';" value="查看我的账户余额" />
            </body></html>
        """.trimIndent()
        val redeemableHtml = """
            <html><body>
              <div class="cell">
                <span>您已连续登录 8 天</span>
                <input type="button" onclick="location.href = '/mission/daily/redeem?once=84830';" value="领取每日登录奖励" />
              </div>
            </body></html>
        """.trimIndent()
        val claimedSuccessHtml = """
            <html><body>
              <div class="cell">每日登录奖励已领取</div>
              <input type="button" onclick="location.href = '/balance';" value="查看我的账户余额" />
            </body></html>
        """.trimIndent()
        val stillRedeemableHtml = """
            <html><body>
              <div class="cell">领取成功</div>
              <input type="button" onclick="location.href = '/mission/daily/redeem?once=84830';" value="领取奖励" />
            </body></html>
        """.trimIndent()

        val status = requireNotNull(parser.parseDailyCheckIn(checkedHtml))
        assertThat(status.checkedIn).isTrue()
        assertThat(status.continuousDays).isEqualTo(12)
        assertThat(status.canCheckIn).isFalse()

        val page = requireNotNull(parser.parseDailyCheckInPage(redeemableHtml))
        assertThat(page.checkIn.checkedIn).isFalse()
        assertThat(page.checkIn.canCheckIn).isTrue()
        assertThat(page.checkIn.continuousDays).isEqualTo(8)
        assertThat(page.redeemOnce).isEqualTo("84830")

        assertThat(parser.isDailyCheckInSuccess(claimedSuccessHtml)).isTrue()
        assertThat(parser.isDailyCheckInSuccess(stillRedeemableHtml)).isFalse()
    }

    @Test
    fun accessChallengeAndRiskNotice_detectsChallengesAndIgnoresTopicTitles() {
        assertThat(parser.hasDailyCheckInRiskNotice("<html><body>请用一个干净安装的浏览器重试</body></html>")).isTrue()
        assertThat(parser.hasDailyCheckInRiskNotice("<html><head><title>Just a moment...</title></head><body><div id=\"cf-chl-widget\"></div></body></html>")).isTrue()
        val homeTopicHtml = """
            <div id="Main"><div class="cell item">
              <span class="item_title"><a class="topic-link" href="/t/123">聊聊 Cloudflare 的缓存配置</a></span>
            </div></div>
        """.trimIndent()
        assertThat(parser.hasAccessChallenge(homeTopicHtml)).isFalse()
    }

    @Test
    fun parseLatestDailyRewardAndWealth_handlesAllSupportedMarkupLayouts() {
        val rewardHtml = """
            <html><body><table>
              <tr><td>2026-07-15</td><td>每日登录奖励</td><td>+12</td><td>0</td><td>记录</td></tr>
              <tr><td>2026-07-14</td><td>每日登录奖励</td><td>+8</td><td>0</td><td>记录</td></tr>
            </table></body></html>
        """.trimIndent()
        val zeroRewardHtml = """
            <html><body><table>
              <tr><td>2026-07-15</td><td>主题回复</td><td>+20</td><td>0</td><td>记录</td></tr>
              <tr><td>2026-07-14</td><td>每日登录奖励</td><td>0</td><td>0</td><td>记录</td></tr>
            </table></body></html>
        """.trimIndent()
        assertThat(parser.parseLatestDailyReward(rewardHtml)).isEqualTo(12)
        assertThat(parser.parseLatestDailyReward(zeroRewardHtml)).isNull()

        val tableWealth = """
            <html><body><div class="box"><table>
              <tr><td>金币</td><td><strong>1</strong></td></tr>
              <tr><td>银币</td><td><strong>23</strong></td></tr>
              <tr><td>铜币</td><td><strong>4,567</strong></td></tr>
            </table></div></body></html>
        """.trimIndent()
        val afterIcons = """
            <html><body><div class="box">
              <div class="cell">
                <img src="/static/img/gold.png" alt="gold" /> 1
                <img src="/static/img/silver.png" alt="silver" /> 23
                <img src="/static/img/bronze.png" alt="bronze" /> 4,567
              </div>
              <div class="cell">2026-06-30 每日登录奖励 铜币 10</div>
            </div></body></html>
        """.trimIndent()
        val beforeIcons = """
            <html><body><div class="box">
              <div class="cell">
                6 <img src="/static/img/gold.png" alt="gold" />
                28 <img src="/static/img/silver.png" alt="silver" />
                62 <img src="/static/img/bronze.png" alt="bronze" />
              </div>
              <div class="cell">2026-06-30 每日登录奖励 铜币 10</div>
            </div></body></html>
        """.trimIndent()

        for (html in listOf(tableWealth, afterIcons)) {
            val wealth = requireNotNull(parser.parseAccountWealth(html))
            assertThat(wealth.gold).isEqualTo(1)
            assertThat(wealth.silver).isEqualTo(23)
            assertThat(wealth.bronze).isEqualTo(4567)
        }
        val before = requireNotNull(parser.parseAccountWealth(beforeIcons))
        assertThat(before.gold).isEqualTo(6)
        assertThat(before.silver).isEqualTo(28)
        assertThat(before.bronze).isEqualTo(62)
    }

    @Test
    fun parseAccountActivityPercent_parsesValidPercentagesAndRejectsSpoofsOrInvalidValues() {
        for (value in listOf("0", "59", "100", "59.5")) {
            assertThat(parser.parseAccountActivityPercent(activitySidebar("width: $value%;"))).isEqualTo(value.toFloat())
        }
        assertThat(parser.parseAccountActivityPercent(activitySidebar("background: red; WIDTH : 59.5 % ; height: 3px")))
            .isEqualTo(59.5f)
        val noSignoutHtml = activitySidebar("width: 99%")
            .replace("member-activity-start", "member-activity-fourth")
            .replace("<a href='/signout'>退出</a>", "")
        assertThat(parser.parseAccountActivityPercent(noSignoutHtml)).isEqualTo(99f)

        for (invalid in listOf("", "width: 101%", "width: -1%", "width: NaN%", "width: Infinity%", "width: 59px", "max-width: 59%", "width: calc(59%)")) {
            assertThat(parser.parseAccountActivityPercent(activitySidebar(invalid))).isNull()
        }
        assertThat(parser.parseAccountActivityPercent("<div id='Rightbar'></div>")).isNull()

        val bar = "<div class='member-activity-bar'><div class='member-activity-start' style='width: 99%'></div></div>"
        assertThat(parser.parseAccountActivityPercent("<div id='Main'>$bar</div>")).isNull()
        assertThat(parser.parseAccountActivityPercent(activitySidebar("width: 59%") + "<form action='/signin'></form>")).isNull()
        assertThat(parser.parseAccountActivityPercent(activitySidebar("width: 59%") + "<title>Just a moment...</title><div id='cf-chl-widget'></div>")).isNull()
        assertThat(parser.parseAccountActivityPercent("<div id='Rightbar'>$bar</div>")).isNull()
    }

    @Test
    fun parseUserProfileAndRecentActivity() {
        val profileHtml = """
            <html>
              <head><script type="application/ld+json">
                {"@context":"https://schema.org","@type":"ProfilePage","mainEntity":{"@type":"Person","name":"Mystery0","identifier":"243339"}}
              </script></head>
              <body><div id="Main"><div class="box"><div class="cell">
                <img class="avatar" data-uid="243339" src="//cdn.v2ex.com/avatar/sample_large.png" />
                <span class="gray">
                  V2EX member #243339, joined on 2017-07-20 22:43:23 +08:00
                  <div class="sep5"></div>
                  Today's activity rank <a href="/top/dau">6,695</a>
                </span>
              </div></div></div></body>
            </html>
        """.trimIndent()
        val user = parser.parseUserProfile("Mystery0", profileHtml)
        assertThat(user.memberNumber).isEqualTo(243339)
        assertThat(user.dailyActivityRank).isEqualTo(6695)

        val memberTopicsHtml = """
            <html><body><div class="box"><div class="cell item"><table><tr>
              <td><span class="item_title"><a href="/t/1226707#reply1" class="topic-link">我的历史主题</a></span>
              <span class="topic_info"><a class="node" href="/go/create">分享创造</a> &nbsp;•&nbsp; <strong><a href="/member/Livid">Livid</a></strong> &nbsp;•&nbsp; <span title="2026-07-11 19:21:00 +08:00">20 mins ago</span></span></td>
              <td><a href="/t/1226707#reply1" class="count_livid">1</a></td>
            </tr></table></div></div></body></html>
        """.trimIndent()
        val topics = parser.parseUserRecentTopics(memberTopicsHtml)
        assertThat(topics).hasSize(1)
        assertThat(topics.single().id).isEqualTo(1226707)
        assertThat(topics.single().title).isEqualTo("我的历史主题")

        val recentRepliesHtml = """
            <html><body><div class="box">
              <div class="cell"><span class="gray">Livid's recent replies</span></div>
              <div class="dock_area"><table><tr>
                <td><div class="fr"><span class="fade" title="2026-07-11 19:04:44 +08:00">36 mins ago</span></div>
                <span class="gray">Replied to a topic by <a href="/member/Livid">Livid</a> <span class="chevron">›</span> <a href="/go/wunder">Wunder</a> <span class="chevron">›</span> <a href="/t/1226562#reply4">目标主题标题</a></span></td>
              </tr></table></div>
              <div class="inner"><div class="reply_content">这是一条回复内容</div></div>
            </div></body></html>
        """.trimIndent()
        val replies = parser.parseUserRecentReplies(recentRepliesHtml)
        assertThat(replies).hasSize(1)
        assertThat(replies.single().topicId).isEqualTo(1226562)
        assertThat(replies.single().topicTitle).isEqualTo("目标主题标题")
        assertThat(replies.single().nodeName).isEqualTo("wunder")
        assertThat(replies.single().content).isEqualTo("这是一条回复内容")
        assertThat(replies.single().createdAtEpochSeconds)
            .isEqualTo(OffsetDateTime.parse("2026-07-11T19:04:44+08:00").toEpochSecond())
    }

    @Test
    fun parsePolishNoteAndEditContent() {
        val listHtml = """
            <div class="box">
              <div class="inner"><span class="bigger"><a href="/notes/11" class="black">普通笔记</a></span></div>
              <div class="inner"><span class="bigger"><a href="/notes/67331" class="black">V2EX_Polish_settings{&#34;settings-sync&#34;:{&#34;version&#34;:46（截断</a></span></div>
            </div>
        """.trimIndent()
        assertThat(parser.parsePolishNoteId(listHtml)).isEqualTo(67331L)
        assertThat(parser.parsePolishNoteId("""<div><a href="/notes/11" class="black">普通笔记</a></div>""")).isNull()
        assertThat(parser.parsePolishNoteId("<html><body>空页面</body></html>")).isNull()

        val editHtml = """<form><textarea class="mle" name="content">V2EX_Polish_settings{&#34;member-tag&#34;:{}}</textarea></form>"""
        assertThat(parser.parseNoteEditContent(editHtml)).isEqualTo("""V2EX_Polish_settings{"member-tag":{}}""")
        assertThat(parser.parseNoteEditContent("<html><body>无</body></html>")).isNull()
    }

    private fun activitySidebar(style: String) = """
        <div id='Rightbar'><div class='box'>
          <a href='/member/reader'>reader</a><a href='/signout'>退出</a>
          <a href='/my/nodes'>节点收藏</a><a href='/my/topics'>主题收藏</a><a href='/my/following'>特别关注</a>
          <div class='cell'><div class='member-activity-bar'>
            <div class='member-activity-start' style='$style'></div>
          </div></div>
        </div></div>
    """.trimIndent()
}
