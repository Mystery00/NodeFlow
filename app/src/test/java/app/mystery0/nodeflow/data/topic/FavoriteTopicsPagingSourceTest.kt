package app.mystery0.nodeflow.data.topic

import androidx.paging.PagingSource
import app.mystery0.nodeflow.core.model.FavoriteTopicsPage
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FavoriteTopicsPagingSourceTest {
    @Test
    fun loadsActualNextPageAndDeduplicatesMovedTopics() = runTest {
        val requested = mutableListOf<Int>()
        val source = FavoriteTopicsPagingSource { page ->
            requested += page
            if (page == 1) FavoriteTopicsPage(listOf(topic(1), topic(2)), 2)
            else FavoriteTopicsPage(listOf(topic(2), topic(3)), null)
        }
        val first = source.load(refresh()) as PagingSource.LoadResult.Page
        val next = source.load(PagingSource.LoadParams.Append(2, 20, false)) as PagingSource.LoadResult.Page
        assertThat(requested).containsExactly(1, 2).inOrder()
        assertThat(first.nextKey).isEqualTo(2)
        assertThat(next.data.map { it.id }).containsExactly(3L)
        assertThat(next.nextKey).isNull()
    }

    @Test
    fun retryDoesNotLoseItemsAndCancellationPropagates() = runTest {
        var fail = true
        val source = FavoriteTopicsPagingSource {
            if (fail) throw IllegalStateException("Test failure")
            FavoriteTopicsPage(listOf(topic(1)), null)
        }
        assertThat(source.load(refresh())).isInstanceOf(PagingSource.LoadResult.Error::class.java)
        fail = false
        assertThat((source.load(refresh()) as PagingSource.LoadResult.Page).data).hasSize(1)
        val cancelled = FavoriteTopicsPagingSource { throw CancellationException() }
        assertThat(runCatching { cancelled.load(refresh()) }.exceptionOrNull())
            .isInstanceOf(CancellationException::class.java)
    }

    private fun refresh() = PagingSource.LoadParams.Refresh<Int>(null, 20, false)
    private fun topic(id: Long) = Topic(id, "测试主题", "https://www.v2ex.com/t/$id", Node(name = "android", title = "Android"), User(username = "tester"))
}
