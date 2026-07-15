package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class NodeRemoteDataSourceTest {
    private val parser = V2exHtmlParser()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun node_fillsMissingApiIconFromNodeHtml() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {
                  "id": 39,
                  "name": "android",
                  "title": "Android",
                  "topics": 12887
                }
            """.trimIndent(),
            nodeHtml = """
                <html>
                  <head>
                    <script type="application/ld+json">
                      {
                        "@type": "CollectionPage",
                        "name": "Android",
                        "image": "https://cdn.v2ex.com/navatar/d67d/8ab4/39_xxxlarge.png?m=1754172750"
                      }
                    </script>
                  </head>
                  <body><h1>Android</h1></body>
                </html>
            """.trimIndent(),
        )
        val dataSource = NodeRemoteDataSource(api, json, parser)

        val node = dataSource.node("android")

        assertThat(node.id).isEqualTo(39)
        assertThat(node.name).isEqualTo("android")
        assertThat(node.title).isEqualTo("Android")
        assertThat(node.avatarUrl).isEqualTo("https://cdn.v2ex.com/navatar/d67d/8ab4/39_xxxlarge.png?m=1754172750")
        assertThat(api.nodeTopicsHtmlRequests).containsExactly(NodeTopicsHtmlRequest("android", null))
    }

    @Test
    fun topics_throwsAccessDeniedWhenNodeRedirectsToHome() = runTest {
        val api = FakeV2exRawApi(
            nodeHtml = topicListHtml(topicId = 9001, title = "首页主题"),
            nodeTopicsFinalUrl = "https://www.v2ex.com/",
        )
        val dataSource = NodeRemoteDataSource(api, json, parser)

        val result = runCatching {
            dataSource.topics(name = "flamewar", page = 1)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(error.message).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
        assertThat(api.nodeTopicsHtmlRequests)
            .containsExactly(NodeTopicsHtmlRequest("flamewar", null))
    }

    @Test
    fun topics_parsesPublicNodeWhenFinalUrlRemainsNodePath() = runTest {
        val api = FakeV2exRawApi(
            nodeHtml = topicListHtml(topicId = 9002, title = "Android 主题"),
            nodeTopicsFinalUrl = "https://www.v2ex.com/go/android?p=2",
        )
        val dataSource = NodeRemoteDataSource(api, json, parser)

        val topics = dataSource.topics(name = "android", page = 2)

        assertThat(topics.map { it.id }).containsExactly(9002L)
        assertThat(topics.single().node.name).isEqualTo("android")
        assertThat(api.nodeTopicsHtmlRequests)
            .containsExactly(NodeTopicsHtmlRequest("android", 2))
    }

    private fun topicListHtml(topicId: Long, title: String): String =
        """
            <html><body>
              <div class="cell from_1 t_$topicId">
                <span class="item_title">
                  <a class="topic-link" href="/t/$topicId">$title</a>
                </span>
                <span class="topic_info">
                  <strong><a href="/member/alice">alice</a></strong>
                </span>
              </div>
            </body></html>
        """.trimIndent()

    private data class NodeTopicsHtmlRequest(
        val nodeName: String,
        val page: Int?,
    )

    private class FakeV2exRawApi(
        private val nodeJson: String = "{}",
        private val nodeHtml: String = "",
        private val nodeTopicsFinalUrl: String? = null,
    ) : V2exRawApi {
        val nodeTopicsHtmlRequests = mutableListOf<NodeTopicsHtmlRequest>()

        override suspend fun latestTopics(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topic(id: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun replies(topicId: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun node(name: String): Response<ResponseBody> =
            Response.success(nodeJson.toResponseBody("application/json".toMediaType()))

        override suspend fun member(username: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?): Response<ResponseBody> {
            nodeTopicsHtmlRequests += NodeTopicsHtmlRequest(nodeName, page)
            val defaultUrl = "https://www.v2ex.com/go/$nodeName" +
                page?.let { "?p=$it" }.orEmpty()
            return htmlResponse(
                html = nodeHtml,
                finalUrl = nodeTopicsFinalUrl ?: defaultUrl,
            )
        }

        override suspend fun recentTopicsHtml(page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun planesHtml(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topicHtml(topicId: Long, page: Int?): Response<ResponseBody> = htmlResponse("")

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

        override suspend fun redeemDailyMission(once: String, referer: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun balance(): Response<ResponseBody> = htmlResponse("")

        override suspend fun notifications(page: Int): Response<ResponseBody> = htmlResponse("")

        private fun htmlResponse(
            html: String,
            finalUrl: String? = null,
        ): Response<ResponseBody> {
            val body = html.toResponseBody("text/html".toMediaType())
            if (finalUrl == null) return Response.success(body)
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url(finalUrl).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(body, rawResponse)
        }
    }
}
