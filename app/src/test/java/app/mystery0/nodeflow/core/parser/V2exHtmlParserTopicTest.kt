package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.OffsetDateTime

/** 主题列表、主题详情、回复分页、附言、感谢、收藏与回复表单解析测试。 */
class V2exHtmlParserTopicTest {
    private val parser = V2exHtmlParser()

    @Test
    fun parseTopicList_readsDesktopNodeRecentAndMobileTemplates() {
        val nodeHtml = """
            <html><body>
              <div class="cell from_332855 t_1206122"><table><tr>
                <td><a href="/member/Junian"><img src="https://cdn.v2ex.com/avatar/normal.png" class="avatar" alt="Junian" /></a></td>
                <td>
                  <span class="item_title"><a href="/t/1206122#reply17" class="topic-link" id="topic-link-1206122">把电脑伪装成电视，用 DLNA 投屏拿到视频号直播流地址</a></span>
                  <span class="topic_info"><strong><a href="/member/Junian">Junian</a></strong> &nbsp;•&nbsp; <span title="2026-05-14 13:54:31 +08:00">May 14</span> &nbsp;•&nbsp; Lastly replied by <strong><a href="/member/godall">godall</a></strong></span>
                </td>
                <td><a href="/t/1206122#reply17" class="count_livid">17</a></td>
              </tr></table></div>
            </body></html>
        """.trimIndent()
        val recentDesktopHtml = """
            <html><body>
              <div class="cell item"><table><tr>
                <td><a href="/member/jimmyczm"><img src="//cdn.v2ex.com/avatar/recent_normal.png" class="avatar" alt="jimmyczm" /></a></td>
                <td>
                  <span class="item_title"><a href="/t/1224500#reply18" class="topic-link" id="topic-link-1224500">Recent topic</a></span>
                  <div class="sep5"></div>
                  <span class="topic_info"><div class="votes"></div><a class="node" href="/go/bb">Broadband</a> &nbsp;•&nbsp; <strong><a href="/member/jimmyczm">jimmyczm</a></strong> &nbsp;•&nbsp; <span title="2026-07-02 20:38:20 +08:00">1h 28m ago</span></span>
                </td>
                <td><a href="/t/1224500#reply18" class="count_livid">18</a></td>
              </tr></table></div>
            </body></html>
        """.trimIndent()
        val recentMobileHtml = """
            <html><body>
              <div class="cell item"><table><tr>
                <td><a href="/member/geniushui"><img src="//cdn.v2ex.com/avatar/mobile_normal.png" class="avatar" alt="geniushui" /></a></td>
                <td>
                  <span class="small fade"><a class="node" href="/go/create">Create</a> &nbsp;•&nbsp; <strong><a href="/member/geniushui">geniushui</a></strong></span>
                  <div class="sep5"></div>
                  <span class="item_title"><a href="/t/1224603#reply0" class="topic-link" id="topic-link-1224603">Mobile recent topic</a></span>
                </td>
              </tr></table></div>
            </body></html>
        """.trimIndent()

        val nodeTopic = parser.parseTopicList(nodeHtml, sourceNodeName = "python").single()
        assertThat(nodeTopic.id).isEqualTo(1206122)
        assertThat(nodeTopic.author.username).isEqualTo("Junian")
        assertThat(nodeTopic.replyCount).isEqualTo(17)
        assertThat(nodeTopic.node.name).isEqualTo("python")

        val desktopTopic = parser.parseTopicList(recentDesktopHtml).single()
        assertThat(desktopTopic.node.name).isEqualTo("bb")
        assertThat(desktopTopic.node.title).isEqualTo("Broadband")
        assertThat(desktopTopic.lastTouchedAtEpochSeconds).isEqualTo(1782995900)

        val mobileTopic = parser.parseTopicList(recentMobileHtml).single()
        assertThat(mobileTopic.node.name).isEqualTo("create")
        assertThat(mobileTopic.author.username).isEqualTo("geniushui")
        assertThat(mobileTopic.avatarUrl).isEqualTo("https://cdn.v2ex.com/avatar/mobile_normal.png")
    }

    @Test
    fun parseTopicList_readsExplicitPinnedMarkersVotesAndDoesNotInferPinned() {
        val explicitHtml = """
            <html><body>
              <div class="cell item pinned"><a class="topic-link" href="/t/101">class marker</a></div>
              <div class="cell item" data-sticky="true"><a class="topic-link" href="/t/102">data marker</a></div>
              <div class="cell item"><span class="badge">置顶</span><a class="topic-link" href="/t/103">label marker</a></div>
              <div class="cell item item-pinned"><a class="topic-link" href="/t/511">item-pinned</a></div>
              <div class="cell item" style="background-image: url('/static/img/corner_star.png'); background-repeat: no-repeat;"><a class="topic-link" href="/t/501">corner star</a></div>
              <div class="cell item">
                <a class="node" href="/go/promotions">推广</a>
                <a class="topic-link" href="/t/601">voted non-pinned</a>
                <span class="topic_info">
                  <div class="votes"><li class="fa fa-chevron-up"></li> &nbsp;4 &nbsp;&nbsp; </div>
                  <span title="2026-08-09 16:59:24 +08:00">5 days ago</span>
                </span>
              </div>
              <div class="cell item">
                <a class="node" href="/go/create">分享创造</a>
                <a class="topic-link" href="/t/602">newer unvoted</a>
                <span class="topic_info"><div class="votes"></div><span title="2026-08-14 19:30:00 +08:00">1h ago</span></span>
              </div>
            </body></html>
        """.trimIndent()

        val topics = parser.parseTopicList(explicitHtml)

        assertThat(topics.map { it.isPinned })
            .containsExactly(true, true, true, true, true, false, false)
            .inOrder()
        assertThat(topics[5].votes).isEqualTo(4)
        assertThat(topics[6].votes).isEqualTo(0)
    }

    @Test
    fun parseTopicHtml_prefersMainContentIgnoresSidebarNoticeAndRejectsRestrictedSignIn() {
        val withOuterNotice = """
            <html><body>
              <div class="topic_content"><p>节点说明干扰文案</p></div>
              <div id="Main"><div class="box">
                <div class="header"><a href="/go/flamewar">水深火热</a><h1>归档主题</h1><small class="gray"><a href="/member/alice">alice</a></small></div>
                <div class="cell"><div class="topic_content"><p>真实主题正文</p></div></div>
              </div></div>
            </body></html>
        """.trimIndent()
        val sidebarOnlyNotice = """
            <html><body>
              <div id="Main">
                <div class="box">
                  <div class="header"><a href="/go/flamewar">水深火热</a><h1>无正文归档</h1><small class="gray"><a href="/member/alice">alice</a></small></div>
                  <div class="topic_buttons">主题操作</div>
                </div>
                <div class="box"><div id="r_17800001" class="cell">
                  <strong><a href="/member/bob">bob</a></strong><span class="no">1</span>
                  <div class="reply_content">可见回复</div>
                </div></div>
              </div>
              <div id="Rightbar"><div id="node_sidebar"><div class="topic_content markdown_body"><p>侧栏说明</p></div></div></div>
            </body></html>
        """.trimIndent()
        val restrictedSignIn = """
            <html><body>
              <div id="problem" class="topic_content">需要登录后访问</div>
              <form action="/signin" method="post"><input type="hidden" name="next" value="/restricted" /><input type="password" name="password" /></form>
            </body></html>
        """.trimIndent()

        val parsedMain = requireNotNull(parser.parseTopicHtml(1221181, withOuterNotice))
        assertThat(parsedMain.contentRendered).contains("真实主题正文")
        assertThat(parsedMain.contentRendered).doesNotContain("节点说明干扰文案")

        val parsedSidebarOnly = requireNotNull(parser.parseTopicHtml(1221181, sidebarOnlyNotice))
        assertThat(parsedSidebarOnly.contentRendered).isEmpty()
        assertThat(parsedSidebarOnly.replies.single().contentRendered).isEqualTo("可见回复")

        assertThat(parser.parseTopicHtml(1221181, restrictedSignIn)).isNull()
        assertThat(parser.parseTopicHtml(1, """<div class="header">登录 V2EX</div><form action="/signin" method="post"><input type="text" name="u" /></form>""")).isNull()
    }

    @Test
    fun parseTopicHtml_readsMetadataJsonLdAndDomHeartFallback() {
        val fullMetadataHtml = """
            <html>
              <head>
                <script type="application/ld+json">
                  {
                    "comment": [
                      {"@type": "Comment", "text": "普通"},
                      {"@type": "Comment", "text": "热1", "interactionStatistic": {"@type": "InteractionCounter", "interactionType": "https://schema.org/LikeAction", "userInteractionCount": 1}},
                      {"@type": "Comment", "text": "热2", "interactionStatistic": {"@type": "InteractionCounter", "interactionType": "https://schema.org/LikeAction", "userInteractionCount": 2}}
                    ],
                    "interactionStatistic": [
                      {"@type": "InteractionCounter", "interactionType": "https://schema.org/ViewAction", "userInteractionCount": 5662},
                      {"@type": "InteractionCounter", "interactionType": "https://schema.org/ReplyAction", "userInteractionCount": 64}
                    ]
                  }
                </script>
              </head>
              <body>
                <h1>coding plan</h1>
                <a href="/go/programmer">程序员</a>
                <small class="gray"><a href="/member/hiboshi">hiboshi</a> · 5662 views</small>
                <div class="topic_content"><p>正文</p></div>
                <div class="cell">
                  <div class="fr">
                    <a href="/tag/Coding" class="tag">Coding</a>
                    <a href="/tag/plan" class="tag">plan</a>
                  </div>
                  <span class="gray">64 replies</span>
                </div>
              </body>
            </html>
        """.trimIndent()
        val heartFallbackHtml = """
            <html><body>
              <h1>DOM 兜底</h1>
              <div class="topic_content"><p>正文</p></div>
              <span class="small fade"><img src="/static/img/heart_20250818.png" alt="heart" /> 1</span>
              <span class="small fade"><img src="/static/img/badge.png" alt="badge" /> badge</span>
            </body></html>
        """.trimIndent()
        val titleOnlyHtml = """
            <html><body><div class="header">
              <a href="/go/programmer">程序员</a><h1>只有标题的主题</h1><small class="gray"><a href="/member/author">author</a></small>
            </div></body></html>
        """.trimIndent()

        val full = requireNotNull(parser.parseTopicHtml(1223541, fullMetadataHtml))
        assertThat(full.viewCount).isEqualTo(5662)
        assertThat(full.hotReplyCount).isEqualTo(2)
        assertThat(full.tags).containsExactly("Coding", "plan").inOrder()

        assertThat(requireNotNull(parser.parseTopicHtml(1, heartFallbackHtml)).hotReplyCount).isEqualTo(1)

        val titleOnly = requireNotNull(parser.parseTopicHtml(1228198, titleOnlyHtml))
        assertThat(titleOnly.viewCount).isNull()
        assertThat(titleOnly.hotReplyCount).isNull()
        assertThat(titleOnly.tags).isEmpty()
        assertThat(titleOnly.replyCount).isNull()
        assertThat(titleOnly.contentRendered).isEmpty()
        assertThat(titleOnly.replies).isEmpty()
    }

    @Test
    fun parseTopicHtml_readsRepliesPaginationHeaderCountFloorOffsetAndExpandedImages() {
        val html = """
            <html><body><div id="Wrapper">
              <div class="header">
                <div class="fr"><a href="/member/adz2k"><img src="//cdn.v2ex.com/gravatar/abc.png" class="avatar" /></a></div>
                <div><a href="/">V2EX</a> <span class="chevron">›</span> <a href="/go/android">Android</a></div>
                <h1>主题标题</h1>
                <small class="gray"><a href="/member/adz2k">adz2k</a> · <span title="2026-07-10 16:33:39 +08:00">20h 5m ago</span> · 1656 views</small>
              </div>
              <div class="cell"><div class="topic_content"><p>正文段落</p></div></div>
              <div class="cell"><span class="gray">342 条回复 &nbsp;•&nbsp; 到目前为止</span></div>
              <div id="r_101" class="cell"><table><tr>
                <td><img src="//cdn.v2ex.com/avatar/x_normal.png" class="avatar" /></td>
                <td>
                  <div class="fr"><span class="no">150</span></div>
                  <strong><a href="/member/xingfu0539">xingfu0539</a></strong>
                  <span class="ago" title="2026-07-10 19:37:31 +08:00">17h 1m ago</span>
                  <div class="reply_content">图片 <a href="https://i.v2ex.co/example.png"><img src="https://i.v2ex.co/example_thumbnail.png"></a></div>
                </td>
              </tr></table></div>
              <div id="r_102" class="cell"><table><tr>
                <td><img src="//cdn.v2ex.com/avatar/y_normal.png" class="avatar" /></td>
                <td>
                  <div class="fr"><span class="small fade"><img src="/static/img/heart_neue@2x.png" alt="❤️" /> 3</span></div>
                  <strong><a href="/member/moefishtang">moefishtang</a></strong>
                  <div class="reply_content">无显式楼层回复</div>
                </td>
              </tr></table></div>
              <input type="number" class="page_input" min="1" max="3" value="1" />
            </div></body></html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(1226421, html, floorOffset = 100))
        assertThat(topic.replyCount).isEqualTo(342)
        assertThat(topic.pageCount).isEqualTo(3)
        assertThat(topic.authorAvatarUrl).isEqualTo("https://cdn.v2ex.com/gravatar/abc.png")
        assertThat(topic.replies[0].floor).isEqualTo(150)
        assertThat(topic.replies[0].content).contains("https://i.v2ex.co/example.png")
        assertThat(topic.replies[0].content).doesNotContain("example_thumbnail.png")
        assertThat(topic.replies[1].floor).isEqualTo(102)
        assertThat(topic.replies[1].thanks).isEqualTo(3)
    }

    @Test
    fun parseTopicHtml_parsesSubtleAppendsThankAndFavoriteStates() {
        val html = """
            <html><body><div id="Main">
              <div class="box">
                <div class="header"><h1>附言与互动</h1><small class="gray"><a href="/member/alice">alice</a></small></div>
                <div class="topic_content">正文</div>
                <div class="subtle">
                  <span class="fade">第 1 条附言 &nbsp;•&nbsp; <span title="2026-07-24 15:30:00 +08:00">3 小时前</span></span>
                  <div class="topic_content">第一条附言</div>
                </div>
                <div class="subtle">
                  <span class="fade">Supplement 2 &nbsp;·&nbsp; <span title="2026-07-24 16:00:00 +08:00">2h ago</span></span>
                  <div class="topic_content">Second append</div>
                </div>
                <div class="topic_buttons">
                  <a href="/favorite/topic/99?once=98765" class="tb">加入收藏</a>
                  <a id="topic_thank" href="#;" onclick="thankTopic(99, '12345');">感谢</a>
                </div>
              </div>
              <div id="r_100" class="cell"><span class="no">1</span><strong><a href="/member/bob">bob</a></strong>
                <div class="reply_content">未感谢</div>
                <div id="thank_area_100"><a class="thank" href="#;" onclick="thankReply(100);">感谢回复者</a></div>
              </div>
              <div id="r_101" class="cell"><span class="no">2</span><strong><a href="/member/carol">carol</a></strong>
                <div class="reply_content">已感谢</div>
                <div id="thank_area_101" class="thanked">感谢已发送</div>
              </div>
            </div></body></html>
        """.trimIndent()
        val favoritedAndThankedHtml = """
            <html><body><div id="Main">
              <div class="header"><h1>已感谢已收藏</h1><small class="gray"><a href="/member/alice">alice</a></small></div>
              <div class="topic_buttons">
                <a href="/unfavorite/topic/99?once=11111" class="tb">取消收藏</a>
                <span id="topic_thank" class="topic_thanked">感谢已发送</span>
              </div>
              <script>var once = 67890;</script>
            </div></body></html>
        """.trimIndent()
        val noOnceFavoriteHtml = """
            <html><body><div id="Main">
              <div class="header"><h1>无 once 收藏</h1><small class="gray"><a href="/member/alice">alice</a></small></div>
              <div class="topic_buttons"><a href="/favorite/topic/99" class="tb">加入收藏</a></div>
            </div></body></html>
        """.trimIndent()

        val topic = requireNotNull(parser.parseTopicHtml(99L, html))
        assertThat(topic.appends).hasSize(2)
        assertThat(topic.appends[0].index).isEqualTo(1)
        assertThat(topic.appends[0].contentRendered).isEqualTo("第一条附言")
        assertThat(topic.appends[0].createdAtEpochSeconds)
            .isEqualTo(OffsetDateTime.parse("2026-07-24T15:30:00+08:00").toEpochSecond())
        assertThat(topic.appends[1].index).isEqualTo(2)
        assertThat(topic.appends[1].contentRendered).isEqualTo("Second append")
        assertThat(topic.isFavorited).isFalse()
        assertThat(topic.favoriteOnce).isEqualTo("98765")
        assertThat(topic.thankOnce).isEqualTo("12345")
        assertThat(topic.isThanked).isFalse()
        assertThat(topic.replies.map { it.isThanked }).containsExactly(false, true).inOrder()

        val thanked = requireNotNull(parser.parseTopicHtml(99L, favoritedAndThankedHtml))
        assertThat(thanked.isFavorited).isTrue()
        assertThat(thanked.favoriteOnce).isEqualTo("11111")
        assertThat(thanked.isThanked).isTrue()
        assertThat(thanked.thankOnce).isEqualTo("67890")

        val noOnce = requireNotNull(parser.parseTopicHtml(99L, noOnceFavoriteHtml))
        assertThat(noOnce.isFavorited).isFalse()
        assertThat(noOnce.favoriteOnce).isNull()
    }

    @Test
    fun parseReplyForm_readsDynamicFieldsAndRejectsWrongTargetOrSignIn() {
        val valid = parser.parseReplyForm(
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
        assertThat(valid?.actionUrl).isEqualTo("https://www.v2ex.com/t/42")
        assertThat(valid?.contentField).isEqualTo("content")
        assertThat(valid?.maxLength).isEqualTo(10_000)
        assertThat(valid?.hiddenFields).containsExactly("once", "redacted", "return_to_page", "3")

        assertThat(parser.parseReplyForm(42, """<form method="post" action="/t/43"><textarea name="content"></textarea></form>""")).isNull()
        assertThat(parser.parseReplyForm(42, """<form method="post" action="/signin"><input type="password" name="password" /><textarea name="content"></textarea></form>""")).isNull()
    }
}
