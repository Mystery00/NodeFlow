package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class NotificationRemoteDataSourceTest {
    @Test
    fun notifications_parsesExpectedPage() = runTest {
        val api = FakeV2exRawApi(notificationHtml())
        val dataSource = NotificationRemoteDataSource(api, V2exHtmlParser())

        val notifications = dataSource.notifications(page = 2)

        assertThat(api.lastPage).isEqualTo(2)
        assertThat(notifications).hasSize(1)
        assertThat(notifications.single().topicId).isEqualTo(99)
    }

    @Test
    fun notifications_reportsAuthenticationExpiry() = runTest {
        val api = FakeV2exRawApi(
            html = "<form action='/signin'><input name='once'></form>",
            finalUrl = "https://www.v2ex.com/signin?next=/notifications",
        )

        val error = runCatching {
            NotificationRemoteDataSource(api, V2exHtmlParser()).notifications(1)
        }.exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Auth)
    }

    @Test
    fun notifications_rejectsUnexpectedOrigin() = runTest {
        val api = FakeV2exRawApi(notificationHtml(), "https://example.com/notifications")

        val error = runCatching {
            NotificationRemoteDataSource(api, V2exHtmlParser()).notifications(1)
        }.exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun notifications_rejectsUnexpectedDocumentInsteadOfReturningEmptyPage() = runTest {
        val api = FakeV2exRawApi("<html><head><title>V2EX</title></head><body><div id='Main'></div></body></html>")

        val error = runCatching {
            NotificationRemoteDataSource(api, V2exHtmlParser()).notifications(1)
        }.exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun notifications_rejectsMalformedNotificationCell() = runTest {
        val api = FakeV2exRawApi(
            "<html><head><title>提醒系统 - V2EX</title></head><body><div id='Main'><div class='cell' id='n_1'>损坏的通知</div></div></body></html>",
        )

        val error = runCatching {
            NotificationRemoteDataSource(api, V2exHtmlParser()).notifications(1)
        }.exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun notifications_acceptsStructurallyValidEmptyPage() = runTest {
        val api = FakeV2exRawApi(
            "<html><head><title>提醒系统 - V2EX</title></head><body><div id='Main'><div class='box'></div></div></body></html>",
        )

        val notifications = NotificationRemoteDataSource(api, V2exHtmlParser()).notifications(1)

        assertThat(notifications).isEmpty()
    }

    private class FakeV2exRawApi(
        private val html: String,
        private val finalUrl: String = "https://www.v2ex.com/notifications?p=1",
    ) : V2exRawApi {
        var lastPage: Int? = null
            private set

        override suspend fun notifications(page: Int): Response<ResponseBody> {
            lastPage = page
            return htmlResponse(html, finalUrl)
        }

        override suspend fun latestTopics() = emptyResponse()
        override suspend fun topic(id: Long) = emptyResponse()
        override suspend fun replies(topicId: Long) = emptyResponse()
        override suspend fun node(name: String) = emptyResponse()
        override suspend fun member(username: String) = emptyResponse()
        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?) = emptyResponse()
        override suspend fun recentTopicsHtml(page: Int?) = emptyResponse()
        override suspend fun planesHtml() = emptyResponse()
        override suspend fun topicHtml(topicId: Long, page: Int?) = emptyResponse()
        override suspend fun memberHtml(username: String) = emptyResponse()
        override suspend fun signInPage(next: String) = emptyResponse()
        override suspend fun captcha(cacheBust: Long, referer: String) = emptyResponse()
        override suspend fun signIn(fields: Map<String, String>, origin: String, referer: String) = emptyResponse()
        override suspend fun signInTwoFactor(next: String, fields: Map<String, String>, referer: String) = emptyResponse()
        override suspend fun home() = emptyResponse()
        override suspend fun dailyMission() = emptyResponse()
        override suspend fun redeemDailyMission(once: String, referer: String) = emptyResponse()
        override suspend fun balance() = emptyResponse()
        override suspend fun notesHtml() = emptyResponse()
        override suspend fun noteEditHtml(id: Long) = emptyResponse()
        override suspend fun noteEditSubmit(id: Long, content: String, syntax: String) = emptyResponse()
        override suspend fun noteNewSubmit(content: String, syntax: String) = emptyResponse()

        private fun emptyResponse(): Response<ResponseBody> = Response.success("".toResponseBody())

        private fun htmlResponse(html: String, url: String): Response<ResponseBody> {
            val body = html.toResponseBody("text/html".toMediaType())
            val raw = okhttp3.Response.Builder()
                .request(Request.Builder().url(url).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(body, raw)
        }
    }

    private fun notificationHtml() = """
        <html><body><div id="Main">
          <div class="cell" id="n_1"><table><tr>
            <td><a href="/member/actor"><img class="avatar" src="/avatar.png"></a></td>
            <td><span class="fade"><a href="/member/actor">actor</a> 回复了主题 › <a class="topic-link" href="/t/99#reply2">主题</a></span><span class="snow">刚刚</span><div class="payload">内容</div></td>
          </tr></table></div>
        </div></body></html>
    """.trimIndent()
}
