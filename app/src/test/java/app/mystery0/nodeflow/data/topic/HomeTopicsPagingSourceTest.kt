package app.mystery0.nodeflow.data.topic

import androidx.paging.PagingSource
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HomeTopicsPagingSourceTest {
    @Test
    fun load_requestsRequestedPageAndReturnsNextKey() = runTest {
        val requestedPages = mutableListOf<Int>()
        val cachedTopics = mutableListOf<Topic>()
        val pagingSource = HomeTopicsPagingSource(
            loadTopics = { page ->
                requestedPages += page
                listOf(topic(id = page.toLong()))
            },
            cacheTopics = { topics -> cachedTopics += topics },
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = 2,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        assertThat(requestedPages).containsExactly(2)
        assertThat(page.data.map { it.id }).containsExactly(2L)
        assertThat(page.prevKey).isEqualTo(1)
        assertThat(page.nextKey).isEqualTo(3)
        assertThat(cachedTopics.map { it.id }).containsExactly(2L)
    }

    @Test
    fun load_stopsWhenPageIsEmpty() = runTest {
        val cachedTopics = mutableListOf<Topic>()
        val pagingSource = HomeTopicsPagingSource(
            loadTopics = { emptyList() },
            cacheTopics = { topics -> cachedTopics += topics },
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = 3,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        assertThat(page.data).isEmpty()
        assertThat(page.prevKey).isEqualTo(2)
        assertThat(page.nextKey).isNull()
        assertThat(cachedTopics).isEmpty()
    }

    private fun topic(id: Long): Topic = Topic(
        id = id,
        title = "主题 $id",
        url = "https://www.v2ex.com/t/$id",
        node = Node(name = "android", title = "Android"),
        author = User(username = "Livid"),
    )
}
