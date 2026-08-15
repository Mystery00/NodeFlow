package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response
import okhttp3.Response as OkHttpResponse

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
        val dataSource = NodeRemoteDataSource(
            api,
            FakeV2exWriteApi("https://www.v2ex.com/go/android"),
            json,
            parser,
        )

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
        val dataSource = NodeRemoteDataSource(
            api,
            FakeV2exWriteApi("https://www.v2ex.com/go/flamewar"),
            json,
            parser,
        )

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
        val dataSource = NodeRemoteDataSource(
            api,
            FakeV2exWriteApi("https://www.v2ex.com/go/android"),
            json,
            parser,
        )

        val topics = dataSource.topics(name = "android", page = 2)

        assertThat(topics.map { it.id }).containsExactly(9002L)
        assertThat(topics.single().node.name).isEqualTo("android")
        assertThat(api.nodeTopicsHtmlRequests)
            .containsExactly(NodeTopicsHtmlRequest("android", 2))
    }

    @Test
    fun blockNode_readsNodeIdAndOnceBeforeRequestingIgnoreAction() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = """
                <a href="/favorite/node/39?once=12345">收藏节点</a>
            """.trimIndent(),
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "https://www.v2ex.com/go/android")
        val dataSource = NodeRemoteDataSource(
            api = api,
            writeApi = writeApi,
            json = json,
            parser = parser,
        )

        dataSource.blockNode("android")

        assertThat(api.nodeTopicsHtmlRequests).containsExactly(NodeTopicsHtmlRequest("android", null))
        assertThat(writeApi.requestedUrls).containsExactly(
            "https://www.v2ex.com/settings/ignore/node/39?once=12345",
        )
    }

    @Test
    fun blockNode_acceptsHomeRedirectAfterSuccessfulAction() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
        )
        val writeApi = FakeV2exWriteApi(
            finalUrl = "https://www.v2ex.com/",
            responseHtml = """
                <div id="Main">
                  <div class="cell item">
                    <a class="topic-link" href="/t/123">聊聊 Cloudflare 的缓存配置</a>
                  </div>
                </div>
            """.trimIndent(),
        )
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        dataSource.blockNode("android")

        assertThat(writeApi.requestedUrls).hasSize(1)
    }

    @Test
    fun blockNode_rejectsAccessChallengeAtTrustedV2exUrl() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
        )
        val writeApi = FakeV2exWriteApi(
            finalUrl = "https://www.v2ex.com/",
            responseHtml = """
                <html>
                  <head><title>Just a moment...</title></head>
                  <body><div id="cf-chl-widget"></div></body>
                </html>
            """.trimIndent(),
        )
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun blockNode_doesNotWriteWhenNodePageHasNoActionToken() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<html><body>没有节点操作</body></html>",
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "https://www.v2ex.com/go/android")
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Auth)
        assertThat(writeApi.requestedUrls).isEmpty()
    }

    @Test
    fun blockNode_doesNotLoadActionPageOrWriteWhenNodeIdIsMissing() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "https://www.v2ex.com/go/android")
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
        assertThat(api.nodeTopicsHtmlRequests).isEmpty()
        assertThat(writeApi.requestedUrls).isEmpty()
    }

    @Test
    fun blockNode_doesNotWriteWhenActionPageRedirectsToExternalHost() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
            nodeTopicsFinalUrl = "https://untrusted.example/go/android",
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "https://www.v2ex.com/go/android")
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
        assertThat(writeApi.requestedUrls).isEmpty()
    }

    @Test
    fun blockNode_doesNotWriteWhenActionPageRedirectsToDifferentNode() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
            nodeTopicsFinalUrl = "https://www.v2ex.com/go/python",
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "https://www.v2ex.com/go/android")
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
        assertThat(writeApi.requestedUrls).isEmpty()
    }

    @Test
    fun blockNode_acceptsActionEndpointResponse() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
        )
        val writeApi = FakeV2exWriteApi(
            finalUrl = "https://www.v2ex.com/settings/ignore/node/39?once=12345",
        )
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        dataSource.blockNode("android")

        assertThat(writeApi.requestedUrls).hasSize(1)
    }

    @Test
    fun blockNode_rejectsExternalHostWithExpectedPath() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "https://untrusted.example/go/android")
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun blockNode_rejectsInsecureV2exResultUrl() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "http://www.v2ex.com/")
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun blockNode_rejectsNonStandardV2exPort() = runTest {
        val api = FakeV2exRawApi(
            nodeJson = """
                {"id":39,"name":"android","title":"Android","avatar_large":"https://cdn.example/node.png"}
            """.trimIndent(),
            nodeHtml = "<a href=\"/settings/ignore/node/39?once=12345\">屏蔽节点</a>",
        )
        val writeApi = FakeV2exWriteApi(finalUrl = "https://www.v2ex.com:444/")
        val dataSource = NodeRemoteDataSource(api, writeApi, json, parser)

        val result = runCatching { dataSource.blockNode("android") }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
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

        override suspend fun allTopicsHtml(): Response<ResponseBody> = htmlResponse("")

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

        override suspend fun notesHtml(): Response<ResponseBody> = htmlResponse("")

        override suspend fun noteEditHtml(id: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun noteEditSubmit(id: Long, content: String, syntax: String): Response<ResponseBody> =
            htmlResponse("")

        override suspend fun noteNewSubmit(content: String, syntax: String): Response<ResponseBody> =
            htmlResponse("")

        override suspend fun favoriteTopic(
            topicId: Long,
            once: String,
            referer: String
        ): Response<ResponseBody> =
            htmlResponse("")

        override suspend fun unfavoriteTopic(
            topicId: Long,
            once: String,
            referer: String
        ): Response<ResponseBody> =
            htmlResponse("")

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

    private class FakeV2exWriteApi(
        private val finalUrl: String,
        private val responseHtml: String = "<html><body>节点页面</body></html>",
    ) : V2exWriteApi {
        val requestedUrls = mutableListOf<String>()

        override suspend fun getHtml(url: String): Response<ResponseBody> {
            requestedUrls += url
            val body = responseHtml.toResponseBody("text/html".toMediaType())
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url(finalUrl).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(body, rawResponse)
        }

        override suspend fun submitForm(
            url: String,
            body: RequestBody,
            origin: String,
            referer: String,
        ): Response<ResponseBody> = error("Unexpected form submit")

        override suspend fun uploadImage(
            body: RequestBody,
            accept: String,
            requestedWith: String,
            referer: String,
        ): Response<ResponseBody> = error("Unexpected image upload")
    }
}
