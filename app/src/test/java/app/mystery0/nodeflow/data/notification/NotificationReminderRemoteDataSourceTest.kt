package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit

class NotificationReminderRemoteDataSourceTest {
    @Test
    fun unreadCountReadsLoggedInHome() = runTest {
        val source = NotificationReminderRemoteDataSource(
            FakeApi("<a href='/signout'>out</a><input class='super special button' value='3 条未读提醒'>"),
            V2exHtmlParser(),
        )

        assertThat(source.unreadCount()).isEqualTo(3)
    }

    @Test
    fun unreadCountReadsZeroFromLoggedInHome() = runTest {
        val source = NotificationReminderRemoteDataSource(
            FakeApi("<a href='/signout'>out</a>"),
            V2exHtmlParser(),
        )

        assertThat(source.unreadCount()).isEqualTo(0)
    }

    @Test
    fun anonymousHomeWithoutRedirectIsAuthenticationError() = runTest {
        val source = NotificationReminderRemoteDataSource(
            FakeApi("<form action='/signin'></form>"),
            V2exHtmlParser(),
        )

        val error = runCatching { source.unreadCount() }.exceptionOrNull()

        assertThat(error).isInstanceOf(NodeFlowException::class.java)
        assertThat((error as NodeFlowException).kind).isEqualTo(NodeFlowException.Kind.Auth)
    }

    @Test
    fun anonymousHomeIsAuthenticationError() = runTest {
        val source = NotificationReminderRemoteDataSource(
            FakeApi("<form action='/signin'></form>", "https://www.v2ex.com/signin"),
            V2exHtmlParser(),
        )

        val error = runCatching { source.unreadCount() }.exceptionOrNull()

        assertThat(error).isInstanceOf(NodeFlowException::class.java)
        assertThat((error as NodeFlowException).kind).isEqualTo(NodeFlowException.Kind.Auth)
    }

    @Test
    fun accessChallengeIsTemporaryAccessDenied() = runTest {
        val source = NotificationReminderRemoteDataSource(
            FakeApi("<div id='cf-chl-widget'></div>"),
            V2exHtmlParser(),
        )

        val error = runCatching { source.unreadCount() }.exceptionOrNull()

        assertThat(error).isInstanceOf(NodeFlowException::class.java)
        assertThat((error as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun mockWebServerRedirectToSigninIsAuthenticationError() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/signin"))
            server.enqueue(MockResponse().setBody("<form action='/signin'></form>"))
            val source = NotificationReminderRemoteDataSource(
                api = serverApi(server),
                parser = V2exHtmlParser(),
                expectedHost = server.hostName,
                expectedScheme = "http",
            )

            val error = runCatching { source.unreadCount() }.exceptionOrNull()

            assertThat(error).isInstanceOf(NodeFlowException::class.java)
            assertThat((error as NodeFlowException).kind).isEqualTo(NodeFlowException.Kind.Auth)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun mockWebServerAnonymousHomeIsAuthenticationError() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("<form action='/signin'></form>"))
            val source = NotificationReminderRemoteDataSource(
                api = serverApi(server),
                parser = V2exHtmlParser(),
                expectedHost = server.hostName,
                expectedScheme = "http",
            )

            val error = runCatching { source.unreadCount() }.exceptionOrNull()

            assertThat(error).isInstanceOf(NodeFlowException::class.java)
            assertThat((error as NodeFlowException).kind).isEqualTo(NodeFlowException.Kind.Auth)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun mockWebServerNonSuccessfulResponseIsNotTreatedAsZero() = runTest {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503))
            val source = NotificationReminderRemoteDataSource(
                api = serverApi(server),
                parser = V2exHtmlParser(),
                expectedHost = server.hostName,
                expectedScheme = "http",
            )

            val error = runCatching { source.unreadCount() }.exceptionOrNull()

            assertThat(error).isInstanceOf(NodeFlowException::class.java)
            assertThat((error as NodeFlowException).kind).isEqualTo(NodeFlowException.Kind.Http)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun nonSuccessfulResponseIsNotTreatedAsZero() = runTest {
        val source = NotificationReminderRemoteDataSource(
            FakeApi("", code = 503),
            V2exHtmlParser(),
        )

        val error = runCatching { source.unreadCount() }.exceptionOrNull()

        assertThat(error).isInstanceOf(NodeFlowException::class.java)
        assertThat((error as NodeFlowException).kind).isEqualTo(NodeFlowException.Kind.Http)
    }

    private fun serverApi(server: MockWebServer): V2exRawApi = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .client(OkHttpClient())
        .build()
        .create(V2exRawApi::class.java)

    private class FakeApi(
        private val html: String,
        private val url: String = "https://www.v2ex.com/",
        private val code: Int = 200,
    ) : V2exRawApi {
        override suspend fun home(): Response<ResponseBody> {
            val raw = okhttp3.Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("test")
                .build()
            return if (code in 200..299) {
                Response.success(html.toResponseBody("text/html".toMediaType()), raw)
            } else {
                Response.error(html.toResponseBody("text/html".toMediaType()), raw)
            }
        }

        private fun empty() = Response.success("".toResponseBody())
        override suspend fun latestTopics() = empty()
        override suspend fun topic(id: Long) = empty()
        override suspend fun replies(topicId: Long) = empty()
        override suspend fun node(name: String) = empty()
        override suspend fun member(username: String) = empty()
        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?) = empty()
        override suspend fun recentTopicsHtml(page: Int?) = empty()
        override suspend fun favoriteTopicsHtml(page: Int): Response<ResponseBody> = error("Unexpected favorites request")

        override suspend fun allTopicsHtml() = empty()
        override suspend fun planesHtml() = empty()
        override suspend fun topicHtml(topicId: Long, page: Int?) = empty()
        override suspend fun memberHtml(username: String) = empty()
        override suspend fun signInPage(next: String) = empty()
        override suspend fun captcha(cacheBust: Long, referer: String) = empty()
        override suspend fun signIn(fields: Map<String, String>, origin: String, referer: String) = empty()
        override suspend fun signInTwoFactor(next: String, fields: Map<String, String>, referer: String) = empty()
        override suspend fun dailyMission() = empty()
        override suspend fun redeemDailyMission(once: String, referer: String) = empty()
        override suspend fun balance() = empty()
        override suspend fun notifications(page: Int) = empty()
        override suspend fun notesHtml() = empty()
        override suspend fun noteEditHtml(id: Long) = empty()
        override suspend fun noteEditSubmit(id: Long, content: String, syntax: String) = empty()
        override suspend fun noteNewSubmit(content: String, syntax: String) = empty()
        override suspend fun favoriteTopic(topicId: Long, once: String, referer: String) = empty()
        override suspend fun unfavoriteTopic(topicId: Long, once: String, referer: String) = empty()
    }
}
