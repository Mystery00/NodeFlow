package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.entity.TopicEntity
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TopicLocalDataSourceTest {
    @Test
    fun cacheTopicDetail_preservesPinnedStateFromExistingListCache() = runTest {
        val dao = FakeTopicDao(
            initial = topic(isPinned = true).toEntity(cachedAtEpochMillis = 1L),
        )
        val dataSource = TopicLocalDataSource(dao)
        val detail = TopicDetail(
            topic = topic(isPinned = false),
            content = "正文",
            contentRendered = "<p>正文</p>",
            replies = emptyList(),
        )

        dataSource.cacheTopicDetail(detail)

        assertThat(dao.current?.isPinned).isTrue()
        assertThat(dao.current?.contentRendered).isEqualTo("<p>正文</p>")
    }

    @Test
    fun cacheTopicDetail_preservesVotesFromExistingListCache() = runTest {
        val dao = FakeTopicDao(
            initial = topic(votes = 4).toEntity(cachedAtEpochMillis = 1L),
        )
        val dataSource = TopicLocalDataSource(dao)
        val detail = TopicDetail(
            topic = topic(votes = 0),
            content = "正文",
            contentRendered = "<p>正文</p>",
            replies = emptyList(),
        )

        dataSource.cacheTopicDetail(detail)

        assertThat(dao.current?.votes).isEqualTo(4)
        assertThat(dao.current?.contentRendered).isEqualTo("<p>正文</p>")
    }

    private fun topic(
        isPinned: Boolean = false,
        votes: Int = 0,
    ): Topic = Topic(
        id = 42L,
        title = "测试主题",
        url = "https://www.v2ex.com/t/42",
        node = Node(name = "android", title = "Android"),
        author = User(username = "alice"),
        isPinned = isPinned,
        votes = votes,
    )

    private class FakeTopicDao(initial: TopicEntity) : TopicDao {
        var current: TopicEntity? = initial

        override suspend fun latestTopics(limit: Int): List<TopicEntity> = listOfNotNull(current)

        override suspend fun topicsByNode(nodeName: String, limit: Int): List<TopicEntity> =
            listOfNotNull(current).filter { it.nodeName == nodeName }

        override suspend fun topic(id: Long): TopicEntity? = current?.takeIf { it.id == id }

        override suspend fun upsertTopics(topics: List<TopicEntity>) {
            current = topics.firstOrNull()
        }

        override suspend fun upsertTopic(topic: TopicEntity) {
            current = topic
        }

        override suspend fun clearTopicDetail(topicId: Long) {
            current = current?.copy(content = null, contentRendered = null)
        }

        override suspend fun clear() {
            current = null
        }
    }
}
