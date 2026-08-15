package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.common.NodeFlowException
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit

class V2exAccessGuardTest {
    private lateinit var server: MockWebServer
    private lateinit var api: V2exRawApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .build()
            .create(V2exRawApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun topic_followsRedirectChainAndRejectsSignInFinalUrl() = runTest {
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/restricted"),
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", "/signin?next=%2Frestricted"),
        )
        server.enqueue(MockResponse().setBody("<html><body>Sign in</body></html>"))

        val response = api.topicHtml(topicId = 1221181)
        val result = runCatching {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
        }

        assertThat(response.raw().request.url.encodedPath).isEqualTo("/signin")
        assertThat(response.raw().request.url.queryParameter("next")).isEqualTo("/restricted")
        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(error.message).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
        assertThat(
            List(3) { server.takeRequest().path },
        ).containsExactly(
            "/t/1221181",
            "/restricted",
            "/signin?next=%2Frestricted",
        ).inOrder()
    }

    @Test
    fun topic_rejectsRestrictedFinalUrl() {
        val response = successfulHtmlResponse(
            html = "<html><body>Restricted</body></html>",
            finalUrl = "https://www.v2ex.com/restricted",
        )

        val result = runCatching {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun topic_rejectsPasswordSignInFormEvenAtTopicUrl() {
        val response = successfulHtmlResponse(
            html = """
                <form action="/signin">
                  <input type="password" name="password" />
                </form>
            """.trimIndent(),
            finalUrl = "https://www.v2ex.com/t/1221181",
        )

        val result = runCatching {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun topic_rejectsRestrictedNextSignInFormWithoutPassword() {
        val response = successfulHtmlResponse(
            html = """
                <form action="/signin">
                  <input type="hidden" name="next" value="/restricted" />
                </form>
            """.trimIndent(),
            finalUrl = "https://www.v2ex.com/t/1221181",
        )

        val result = runCatching {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun topic_allowsPublicPageThatOnlyContainsSignInLink() {
        val html = """
            <a href="/signin">登录</a>
            <div id="Main">
              <div class="topic_content">公开正文</div>
            </div>
        """.trimIndent()
        val response = successfulHtmlResponse(
            html = html,
            finalUrl = "https://www.v2ex.com/t/1",
        )

        val actual = response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)

        assertThat(actual).isEqualTo(html)
    }

    @Test
    fun nodeTopics_rejectsHomeRedirect() {
        val response = successfulHtmlResponse(
            html = "<html><body>Home topics</body></html>",
            finalUrl = "https://www.v2ex.com/",
        )

        val result = runCatching {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.NodeTopics)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun actionResult_allowsHomeRedirect() {
        val html = "<html><body>Home topics</body></html>"
        val response = successfulHtmlResponse(
            html = html,
            finalUrl = "https://www.v2ex.com/",
        )

        val actual = response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.ActionResult)

        assertThat(actual).isEqualTo(html)
    }

    @Test
    fun actionResult_rejectsSignInFinalUrl() {
        val response = successfulHtmlResponse(
            html = "<html><body>Sign in</body></html>",
            finalUrl = "https://www.v2ex.com/signin?next=%2F",
        )

        val result = runCatching {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.ActionResult)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun actionResult_rejectsRestrictedSignInForm() {
        val response = successfulHtmlResponse(
            html = """
                <form action="/signin">
                  <input type="hidden" name="next" value="/restricted" />
                </form>
            """.trimIndent(),
            finalUrl = "https://www.v2ex.com/",
        )

        val result = runCatching {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.ActionResult)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    private fun successfulHtmlResponse(
        html: String,
        finalUrl: String,
    ): Response<ResponseBody> {
        val rawResponse = OkHttpResponse.Builder()
            .request(Request.Builder().url(finalUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        return Response.success(
            html.toResponseBody("text/html".toMediaType()),
            rawResponse,
        )
    }
}
