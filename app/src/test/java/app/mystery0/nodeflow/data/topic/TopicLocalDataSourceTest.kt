package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.testing.FakeTopicDao
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

        assertThat(dao.topic(42L)?.isPinned).isTrue()
        assertThat(dao.topic(42L)?.contentRendered).isEqualTo("<p>正文</p>")
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

        assertThat(dao.topic(42L)?.votes).isEqualTo(4)
        assertThat(dao.topic(42L)?.contentRendered).isEqualTo("<p>正文</p>")
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
}
