package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class TopicRemoteDataSourceTest {
    private val parser = V2exHtmlParser()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun latestTopics_readsHomeTopicsFromRecentHtml() = runTest {
        val api = FakeV2exRawApi(
            recentHtml = """
                <html>
                  <body>
                    <div class="cell from_1 t_1224588">
                      <table>
                        <tr>
                          <td>
                            <a href="/member/Livid">
                              <img src="//cdn.v2ex.com/avatar/sample_normal.png" class="avatar" alt="Livid" />
                            </a>
                          </td>
                          <td>
                            <span class="item_title">
                              <a href="/t/1224588#reply6" class="topic-link" id="topic-link-1224588">首页主题</a>
                            </span>
                            <span class="topic_info">
                              <a href="/go/android">Android</a>
                              &nbsp;•&nbsp;
                              <strong><a href="/member/Livid">Livid</a></strong>
                              &nbsp;•&nbsp;
                              <span title="2026-07-02 20:10:00 +08:00">1 分钟前</span>
                            </span>
                          </td>
                          <td><a href="/t/1224588#reply6" class="count_livid">6</a></td>
                        </tr>
                      </table>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )
        val dataSource = TopicRemoteDataSource(api, json, parser)

        val topics = dataSource.latestTopics()

        assertThat(api.latestTopicsRequestCount).isEqualTo(0)
        assertThat(api.nodeTopicsHtmlRequests).isEmpty()
        assertThat(api.recentTopicsHtmlRequests).containsExactly(RecentTopicsHtmlRequest(1))
        assertThat(topics).hasSize(1)
        assertThat(topics.first().id).isEqualTo(1224588)
        assertThat(topics.first().title).isEqualTo("首页主题")
        assertThat(topics.first().node.name).isEqualTo("android")
        assertThat(topics.first().node.title).isEqualTo("Android")
        assertThat(topics.first().replyCount).isEqualTo(6)
    }

    @Test
    fun homeTopics_readsRequestedRecentPage() = runTest {
        val api = FakeV2exRawApi(
            recentHtml = """
                <html>
                  <body>
                    <div class="cell from_1 t_1224599">
                      <a href="/t/1224599" class="topic-link">第二页主题</a>
                      <span class="topic_info">
                        <a href="/go/programmer">程序员</a>
                        <strong><a href="/member/Livid">Livid</a></strong>
                      </span>
                    </div>
                  </body>
                </html>
            """.trimIndent(),
        )
        val dataSource = TopicRemoteDataSource(api, json, parser)

        val topics = dataSource.homeTopics(page = 2)

        assertThat(api.nodeTopicsHtmlRequests).isEmpty()
        assertThat(api.recentTopicsHtmlRequests).containsExactly(RecentTopicsHtmlRequest(2))
        assertThat(topics.map { it.id }).containsExactly(1224599L)
    }

    @Test
    fun topicDetail_parsesRepliesFromHtmlAndMergesPagesWithoutJsonApi() = runTest {
        val api = FakeV2exRawApi(
            topicHtmlPages = mapOf(
                null to """
                    <html><body>
                      <div class="header">
                        <div><a href="/go/python">Python</a></div>
                        <h1>测试主题</h1>
                        <small class="gray"><a href="/member/alice">alice</a> · <span title="2026-07-01 10:00:00 +08:00">now</span> · 100 views</small>
                      </div>
                      <div class="topic_content"><p>正文内容</p></div>
                      <div id="r_1" class="cell"><table><tr><td>
                        <div class="fr"><span class="no">1</span></div>
                        <strong><a href="/member/bob" class="dark">bob</a></strong>
                        <span class="ago" title="2026-07-01 11:00:00 +08:00">now</span>
                        <div class="reply_content">第一层</div>
                      </td></tr></table></div>
                      <input class="page_input" type="number" max="2" />
                    </body></html>
                """.trimIndent(),
                2 to """
                    <html><body>
                      <div class="topic_content"><p>正文内容</p></div>
                      <div id="r_2" class="cell"><table><tr><td>
                        <div class="fr"><span class="no">2</span></div>
                        <strong><a href="/member/carol" class="dark">carol</a></strong>
                        <span class="ago" title="2026-07-01 12:00:00 +08:00">now</span>
                        <div class="reply_content">第二层</div>
                      </td></tr></table></div>
                    </body></html>
                """.trimIndent(),
            ),
        )
        val dataSource = TopicRemoteDataSource(api, json, parser)

        val detail = dataSource.topicDetail(topicId = 1000)

        assertThat(api.topicJsonCalls).isEqualTo(0)
        assertThat(api.repliesJsonCalls).isEqualTo(0)
        assertThat(api.topicHtmlRequests).containsExactly(null, 2).inOrder()
        assertThat(detail.contentRendered).contains("正文内容")
        assertThat(detail.topic.node.name).isEqualTo("python")
        assertThat(detail.topic.author.username).isEqualTo("alice")
        assertThat(detail.topic.replyCount).isEqualTo(2)
        assertThat(detail.replies.map { it.author.username }).containsExactly("bob", "carol").inOrder()
        assertThat(detail.replies.map { it.floor }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun topicDetail_fallsBackToJsonWhenHtmlIsNotTopicPage() = runTest {
        val api = FakeV2exRawApi(
            topicHtmlPages = mapOf(
                null to """
                    <html><body>
                      <div class="box">
                        <div class="header">登录 V2EX</div>
                        <form action="/signin" method="post"><input type="text" name="u" /></form>
                      </div>
                    </body></html>
                """.trimIndent(),
            ),
            topicJson = """
                [{"id": 2000, "title": "JSON 主题", "content": "raw", "content_rendered": "<p>渲染正文</p>", "replies": 1}]
            """.trimIndent(),
            repliesJson = """
                [{"id": 9, "topic_id": 2000, "content_rendered": "<p>JSON 回复</p>", "member": {"username": "dave"}}]
            """.trimIndent(),
        )
        val dataSource = TopicRemoteDataSource(api, json, parser)

        val detail = dataSource.topicDetail(topicId = 2000)

        assertThat(api.topicJsonCalls).isEqualTo(1)
        assertThat(api.repliesJsonCalls).isEqualTo(1)
        assertThat(detail.contentRendered).isEqualTo("<p>渲染正文</p>")
        assertThat(detail.replies.single().author.username).isEqualTo("dave")
    }

    private data class NodeTopicsHtmlRequest(
        val nodeName: String,
        val page: Int?,
    )

    private data class RecentTopicsHtmlRequest(
        val page: Int?,
    )

    private class FakeV2exRawApi(
        private val recentHtml: String = "",
        private val topicHtmlPages: Map<Int?, String> = emptyMap(),
        private val topicJson: String = "[]",
        private val repliesJson: String = "[]",
    ) : V2exRawApi {
        var latestTopicsRequestCount: Int = 0
        var topicJsonCalls: Int = 0
        var repliesJsonCalls: Int = 0
        val nodeTopicsHtmlRequests = mutableListOf<NodeTopicsHtmlRequest>()
        val recentTopicsHtmlRequests = mutableListOf<RecentTopicsHtmlRequest>()
        val topicHtmlRequests = mutableListOf<Int?>()

        override suspend fun latestTopics(): Response<ResponseBody> {
            latestTopicsRequestCount += 1
            return htmlResponse("[]")
        }

        override suspend fun topic(id: Long): Response<ResponseBody> {
            topicJsonCalls += 1
            return htmlResponse(topicJson)
        }

        override suspend fun replies(topicId: Long): Response<ResponseBody> {
            repliesJsonCalls += 1
            return htmlResponse(repliesJson)
        }

        override suspend fun node(name: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun member(username: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?): Response<ResponseBody> {
            nodeTopicsHtmlRequests += NodeTopicsHtmlRequest(nodeName, page)
            return htmlResponse("")
        }

        override suspend fun recentTopicsHtml(page: Int?): Response<ResponseBody> {
            recentTopicsHtmlRequests += RecentTopicsHtmlRequest(page)
            return htmlResponse(recentHtml)
        }

        override suspend fun planesHtml(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topicHtml(topicId: Long, page: Int?): Response<ResponseBody> {
            topicHtmlRequests += page
            return htmlResponse(topicHtmlPages[page].orEmpty())
        }

        override suspend fun memberHtml(username: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun signInPage(next: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun captcha(cacheBust: Long, referer: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun signIn(
            fields: Map<String, String>,
            origin: String,
            referer: String,
        ): Response<ResponseBody> = htmlResponse("")

        override suspend fun signInTwoFactor(
            next: String,
            fields: Map<String, String>,
            referer: String,
        ): Response<ResponseBody> = htmlResponse("")

        override suspend fun home(): Response<ResponseBody> = htmlResponse("")

        override suspend fun dailyMission(): Response<ResponseBody> = htmlResponse("")

        override suspend fun balance(): Response<ResponseBody> = htmlResponse("")

        private fun htmlResponse(html: String): Response<ResponseBody> =
            Response.success(html.toResponseBody("text/html".toMediaType()))
    }
}
