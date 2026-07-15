package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.model.NotificationReferenceLocator
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationReferenceEnricherTest {
    @Test
    fun enrichNotificationReferences_loadsEachTopicOnceAndBuildsExcerpt() = runTest {
        var loadCalls = 0
        val notifications = listOf(notification(1), notification(2))

        val enriched = enrichNotificationReferences(notifications) {
            loadCalls += 1
            Result.success(topicDetail())
        }

        assertThat(loadCalls).isEqualTo(1)
        assertThat(enriched).hasSize(2)
        assertThat(enriched.first().reference?.floor).isEqualTo(3)
        assertThat(enriched.first().reference?.author?.username).isEqualTo("original")
        assertThat(enriched.first().reference?.excerpt).isEqualTo("原回复内容")
    }

    @Test
    fun enrichNotificationReferences_keepsNotificationWhenReferenceCannotBeResolved() = runTest {
        val notification = notification(1).copy(
            referenceLocator = NotificationReferenceLocator(username = "someoneElse", floor = 3),
        )

        val enriched = enrichNotificationReferences(listOf(notification)) { Result.success(topicDetail()) }

        assertThat(enriched.single().reference).isNull()
    }

    private fun notification(id: Long) = Notification(
        id = id,
        actor = User(username = "actor"),
        action = "回复了你",
        topicId = 99,
        topicTitle = "测试主题",
        replyFloor = 5,
        relativeTime = "刚刚",
        referenceLocator = NotificationReferenceLocator(username = "original", floor = 3),
    )

    private fun topicDetail(): TopicDetail {
        val topic = Topic(
            id = 99,
            title = "测试主题",
            url = "https://www.v2ex.com/t/99",
            node = Node(name = "test", title = "测试"),
            author = User(username = "author"),
        )
        return TopicDetail(
            topic = topic,
            content = "",
            contentRendered = "",
            replies = listOf(
                Reply(
                    id = 300,
                    topicId = 99,
                    floor = 3,
                    author = User(username = "original"),
                    content = "原回复内容",
                    contentRendered = "<p>原回复内容</p>",
                ),
            ),
        )
    }
}
