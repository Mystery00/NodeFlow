package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.V2exUserAgents
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import retrofit2.Retrofit

class FavoriteTopicsRemoteDataSourceTest {
    @Test
    fun requestsFavoritePageWithExistingDesktopPolicy() = runTest {
        withServer { server ->
            server.enqueue(MockResponse().setBody(emptyPage))
            val page = dataSource(server, "https://www.v2ex.com/my/topics?p=2").favoriteTopics(2)
            val request = server.takeRequest()
            assertThat(request.path).isEqualTo("/my/topics?p=2")
            assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.DESKTOP)
            assertThat(page.topics).isEmpty()
            assertThat(page.nextPage).isNull()
        }
    }

    @Test
    fun rejectsRedirectsInsteadOfShowingEmptyCollection() = runTest {
        val cases = mapOf(
            "https://www.v2ex.com/signin?next=/my/topics" to NodeFlowException.Kind.Auth,
            "https://www.v2ex.com/restricted" to NodeFlowException.Kind.AccessDenied,
            "https://www.v2ex.com/" to NodeFlowException.Kind.Parse,
            "https://example.com/my/topics?p=1" to NodeFlowException.Kind.Parse,
            "http://www.v2ex.com/my/topics?p=1" to NodeFlowException.Kind.Parse,
            "https://www.v2ex.com:444/my/topics?p=1" to NodeFlowException.Kind.Parse,
            "https://www.v2ex.com/my/topics?p=2" to NodeFlowException.Kind.Parse,
        )
        withServer { server ->
            cases.forEach { (url, expected) ->
                server.enqueue(MockResponse().setBody(emptyPage))
                val error = runCatching { dataSource(server, url).favoriteTopics(1) }.exceptionOrNull()
                assertThat((error as NodeFlowException).kind).isEqualTo(expected)
            }
        }
    }

    @Test
    fun rejectsLoginChallengeMalformedAndHttpErrorResponses() = runTest {
        val cases = listOf(
            MockResponse().setBody("<a href='/signin'>登录</a>") to NodeFlowException.Kind.Auth,
            MockResponse().setBody("<form action='/signin'><input type='hidden' name='next' value='/restricted'></form>") to NodeFlowException.Kind.AccessDenied,
            MockResponse().setBody("<title>Just a moment...</title><div id='challenge-running'></div>") to NodeFlowException.Kind.AccessDenied,
            MockResponse().setBody("<div id='Main'><div class='box'>未知页面</div></div>") to NodeFlowException.Kind.Parse,
            MockResponse().setResponseCode(503).setBody(emptyPage) to NodeFlowException.Kind.Http,
        )
        withServer { server ->
            val source = dataSource(server, "https://www.v2ex.com/my/topics?p=1")
            cases.forEach { (response, expected) ->
                server.enqueue(response)
                val error = runCatching { source.favoriteTopics(1) }.exceptionOrNull()
                assertThat((error as NodeFlowException).kind).isEqualTo(expected)
            }
        }
    }

    private fun dataSource(server: MockWebServer, finalUrl: String): TopicRemoteDataSource {
        // 请求只访问本地 MockWebServer；替换响应 URL 以验证生产环境的可信地址校验。
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.newBuilder().request(response.request.newBuilder().url(finalUrl).build()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl(server.url("/")).client(client).build().create(V2exRawApi::class.java)
        return TopicRemoteDataSource(api, Json, V2exHtmlParser())
    }

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        server.start()
        try { block(server) } finally { server.shutdown() }
    }

    private val emptyPage = "<div id='Main'><div class='box'><div class='header'>V2EX › 我收藏的主题</div><div class='inner'>暂无收藏</div></div></div>"
}
