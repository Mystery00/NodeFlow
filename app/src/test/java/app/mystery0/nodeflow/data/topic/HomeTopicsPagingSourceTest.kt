package app.mystery0.nodeflow.data.topic

import androidx.paging.PagingSource
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
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

    @Test
    fun load_dropsTopicsAlreadySeenInPreviousPages() = runTest {
        // V2EX 列表实时变动，翻页时上一页的主题可能再次出现在下一页；
        // 重复 id 会让 LazyColumn 的 key 冲突直接崩溃，必须在数据层过滤掉
        val pages = mapOf(
            1 to listOf(topic(id = 1), topic(id = 2)),
            2 to listOf(topic(id = 2), topic(id = 3)),
        )
        val cachedTopics = mutableListOf<Topic>()
        val pagingSource = HomeTopicsPagingSource(
            loadTopics = { page -> pages.getValue(page) },
            cacheTopics = { topics -> cachedTopics += topics },
        )

        pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )
        val result = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = 2,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        assertThat(page.data.map { it.id }).containsExactly(3L)
        assertThat(page.nextKey).isEqualTo(3)
        assertThat(cachedTopics.map { it.id }).containsExactly(1L, 2L, 3L)
    }

    @Test
    fun load_keepsPagingWhenPageOnlyContainsSeenTopics() = runTest {
        val pages = mapOf(
            1 to listOf(topic(id = 1), topic(id = 2)),
            2 to listOf(topic(id = 1), topic(id = 2)),
        )
        val pagingSource = HomeTopicsPagingSource(
            loadTopics = { page -> pages.getValue(page) },
            cacheTopics = {},
        )

        pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )
        val result = pagingSource.load(
            PagingSource.LoadParams.Append(
                key = 2,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val page = result as PagingSource.LoadResult.Page
        assertThat(page.data).isEmpty()
        // 原始页非空说明服务端还有数据，不能因为整页都是重复项就终止分页
        assertThat(page.nextKey).isEqualTo(3)
    }

    @Test
    fun load_preservesAccessDeniedErrorWithoutCaching() = runTest {
        val accessDenied = NodeFlowException(
            kind = NodeFlowException.Kind.AccessDenied,
            message = V2EX_ACCESS_DENIED_MESSAGE,
        )
        var cacheCallCount = 0
        val pagingSource = HomeTopicsPagingSource(
            loadTopics = { throw accessDenied },
            cacheTopics = { cacheCallCount += 1 },
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val error = result as PagingSource.LoadResult.Error
        assertThat(error.throwable).isSameInstanceAs(accessDenied)
        assertThat(cacheCallCount).isEqualTo(0)
    }

    private fun topic(id: Long): Topic = Topic(
        id = id,
        title = "主题 $id",
        url = "https://www.v2ex.com/t/$id",
        node = Node(name = "android", title = "Android"),
        author = User(username = "Livid"),
    )
}
