package app.mystery0.nodeflow.core.database.entity

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TopicEntityPinnedTest {
    @Test
    fun topicEntityMapping_preservesPinnedState() {
        val topic = Topic(
            id = 42L,
            title = "置顶主题",
            url = "https://www.v2ex.com/t/42",
            node = Node(name = "android", title = "Android"),
            author = User(username = "alice"),
            isPinned = true,
        )

        val entity = topic.toEntity(cachedAtEpochMillis = 1L)
        val restored = entity.toTopic()

        assertThat(entity.isPinned).isTrue()
        assertThat(restored.isPinned).isTrue()
    }
}
