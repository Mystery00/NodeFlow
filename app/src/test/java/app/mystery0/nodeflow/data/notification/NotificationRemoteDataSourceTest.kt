package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.testing.TestV2exRawApi
import app.mystery0.nodeflow.testing.emptyHtmlResponse
import app.mystery0.nodeflow.testing.testHtmlResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import app.mystery0.nodeflow.core.model.AuthSession
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.Response

class NotificationRemoteDataSourceTest {
    @Test
    fun successfulNotificationLoadClearsUnreadButFailedLoadPreservesIt() = runTest {
        val store = UnreadNotificationStore(MutableStateFlow(AuthSession(username = "reader", cookieHeader = "test")), backgroundScope)
        store.update(store.beginRequest(), 7)
        val failedSource = NotificationRemoteDataSource(FakeV2exRawApi("<html>bad</html>"), V2exHtmlParser(), store)
        assertThat(runCatching { failedSource.notifications(1) }.isFailure).isTrue()
        assertThat(store.unreadCount.first()).isEqualTo(7)
        val source = NotificationRemoteDataSource(FakeV2exRawApi(notificationHtml()), V2exHtmlParser(), store)
        source.notifications(1)
        assertThat(store.unreadCount.first()).isEqualTo(0)
    }

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
    fun notifications_parsesGrayDescriptionFromUpdatedPage() = runTest {
        // 新版页面仅将通知描述的 fade 样式改为 gray，其余结构保持一致。
        val html = notificationHtml().replace("class=\"fade\"", "class=\"gray\"")
        val store = UnreadNotificationStore(MutableStateFlow(AuthSession(username = "reader", cookieHeader = "test")), backgroundScope)
        store.update(store.beginRequest(), 7)
        val source = NotificationRemoteDataSource(FakeV2exRawApi(html), V2exHtmlParser(), store)

        val notification = source.notifications(1).single()

        assertThat(notification.actor.username).isEqualTo("actor")
        assertThat(notification.action).isEqualTo("回复了主题")
        assertThat(notification.topicId).isEqualTo(99)
        assertThat(notification.topicTitle).isEqualTo("主题")
        assertThat(notification.replyFloor).isEqualTo(2)
        assertThat(notification.relativeTime).isEqualTo("刚刚")
        assertThat(notification.contentRendered).isEqualTo("内容")
        assertThat(store.unreadCount.first()).isEqualTo(0)
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
    ) : TestV2exRawApi(fallback = { emptyHtmlResponse() }) {
        var lastPage: Int? = null
            private set

        override suspend fun notifications(page: Int): Response<ResponseBody> {
            lastPage = page
            return testHtmlResponse(html, finalUrl)
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
