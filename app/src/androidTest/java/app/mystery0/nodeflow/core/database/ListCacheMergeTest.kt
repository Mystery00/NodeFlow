package app.mystery0.nodeflow.core.database

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

/** 使用真实 Room 验证列表刷新不会擦除详情，并且显式清理仍然有效。 */
class ListCacheMergeTest {
    @Test
    fun topicListPreservesDetailAndUpdatesSummary() = runBlocking {
        val db = database()
        try {
            val topic = Topic(id = 42, title = "旧标题", url = "https://www.v2ex.com/t/42",
                node = Node(name = "test", title = "测试"), author = User(username = "tester"),
                replyCount = 1, createdAtEpochSeconds = 123)
            val dao = db.topicDao()
            dao.upsertTopic(topic.toEntity(content = "正文", contentRendered = "<p>正文</p>"))
            dao.upsertTopics(listOf(topic.copy(title = "新标题", replyCount = 2, createdAtEpochSeconds = null).toEntity()))
            val cached = checkNotNull(dao.topic(42))
            assertThat(cached.title).isEqualTo("新标题")
            assertThat(cached.replyCount).isEqualTo(2)
            assertThat(cached.contentRendered).isEqualTo("<p>正文</p>")
            assertThat(cached.content).isEqualTo("正文")
            assertThat(cached.createdAtEpochSeconds).isEqualTo(123)
            dao.clearTopicDetail(42)
            assertThat(dao.topic(42)?.contentRendered).isNull()
        } finally { db.close() }
    }

    @Test
    fun nodePlaneRefreshPreservesDetailAndUpdatesTitle() = runBlocking {
        val db = database()
        try {
            val node = Node(name = "test", title = "旧标题", id = 3, header = "节点介绍",
                avatarUrl = "https://example.test/a.png", topics = 12, stars = 4)
            val dao = db.nodeDao()
            dao.upsertNode(node.toEntity())
            dao.replaceNodePlanes(emptyList(), listOf(Node(name = "test", title = "新标题").toEntity()), emptyList())
            val cached = checkNotNull(dao.node("test"))
            assertThat(cached.title).isEqualTo("新标题")
            assertThat(cached.id).isEqualTo(3)
            assertThat(cached.header).isEqualTo("节点介绍")
            assertThat(cached.avatarUrl).isEqualTo("https://example.test/a.png")
            assertThat(cached.topics).isEqualTo(12)
            assertThat(cached.stars).isEqualTo(4)
        } finally { db.close() }
    }

    private fun database() = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, NodeFlowDatabase::class.java,
    ).build()
}
