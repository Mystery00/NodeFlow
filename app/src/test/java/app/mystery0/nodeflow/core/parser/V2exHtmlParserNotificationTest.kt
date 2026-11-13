package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 通知列表解析、未读数校验与通知页受限检测测试。 */
class V2exHtmlParserNotificationTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseUnreadNotificationCount_guardsSpoofsInsideContentWhenImplicitZeroDisallowed() {
        val desktopLink = """
            <html><body>
              <div id="Top"><a href="#;" onclick="location.href='/signout?once=1';">登出</a></div>
              <div id="Rightbar"><a href="/notifications">5 条未读提醒</a></div>
            </body></html>
        """.trimIndent()
        val explicitZero = """
            <html><body>
              <div id="Top"><a href="/signout?once=1">登出</a></div>
              <div id="Rightbar"><a href="/notifications">0 条未读提醒</a></div>
            </body></html>
        """.trimIndent()
        val spoofedTopicContent = """
            <html><body>
              <div class="topic_content"><a href="/notifications">99 条未读提醒</a></div>
            </body></html>
        """.trimIndent()

        assertThat(parser.parseUnreadNotificationCount(desktopLink, allowImplicitZero = false)).isEqualTo(5)
        assertThat(parser.parseUnreadNotificationCount(explicitZero, allowImplicitZero = false)).isEqualTo(0)
        assertThat(parser.parseUnreadNotificationCount(spoofedTopicContent, allowImplicitZero = false)).isNull()
    }

    @Test
    fun parseNotifications_readsReplyReferenceThankAndSkipsMalformedCells() {
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
              <div class="cell" id="n_7"><table><tr>
                <td><a href="/member/helper"><img class="avatar" src="/avatar.png" /></a></td>
                <td>
                  <span class="fade"><a href="/member/helper">helper</a> 感谢了你在主题 › <a class="topic-link" href="/t/99#reply2">感谢主题</a> 里的回复</span>
                  <span class="snow">12 天前</span>
                </td>
              </tr></table></div>
              <div class="cell" id="n_8"><span class="fade">未知提醒</span></div>
            </div></body></html>
        """.trimIndent()

        val notifications = parser.parseNotifications(html)

        assertThat(notifications).hasSize(2)
        val first = notifications[0]
        assertThat(first.id).isEqualTo(12345)
        assertThat(first.actor.username).isEqualTo("replier")
        assertThat(first.actor.avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar.png")
        assertThat(first.action).isEqualTo("回复了你在主题里的回复")
        assertThat(first.topicId).isEqualTo(67890)
        assertThat(first.topicTitle).isEqualTo("测试主题")
        assertThat(first.replyFloor).isEqualTo(5)
        assertThat(first.relativeTime).isEqualTo("2 小时前")
        assertThat(first.contentRendered).contains("回复正文")
        assertThat(first.referenceLocator?.username).isEqualTo("original")
        assertThat(first.referenceLocator?.floor).isEqualTo(3)

        val second = notifications[1]
        assertThat(second.action).isEqualTo("感谢了你在主题里的回复")
        assertThat(second.contentRendered).isNull()
        assertThat(second.referenceLocator).isNull()
    }

    @Test
    fun hasAccessChallenge_ignoresCloudflareMarkerInsideValidNotificationContent() {
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
}
