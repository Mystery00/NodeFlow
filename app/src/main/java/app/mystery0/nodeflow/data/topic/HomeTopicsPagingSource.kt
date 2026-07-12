package app.mystery0.nodeflow.data.topic

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.mystery0.nodeflow.core.model.Topic
import kotlinx.coroutines.CancellationException

class HomeTopicsPagingSource(
    private val loadTopics: suspend (page: Int) -> List<Topic>,
    private val cacheTopics: suspend (List<Topic>) -> Unit,
) : PagingSource<Int, Topic>() {
    // 列表实时变动，翻页时可能返回前面页已出现过的主题；
    // 重复 id 会让列表项 key 冲突直接崩溃，按已加载 id 过滤（每次刷新会重建 PagingSource，集合随之重置）
    private val loadedTopicIds = mutableSetOf<Long>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Topic> {
        val page = params.key ?: FIRST_PAGE
        return try {
            val topics = loadTopics(page)
            val newTopics = topics.filter { loadedTopicIds.add(it.id) }
            if (newTopics.isNotEmpty()) {
                cacheTopics(newTopics)
            }
            LoadResult.Page(
                data = newTopics,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (topics.isEmpty()) null else page + 1,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Topic>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        return anchorPage.prevKey?.plus(1) ?: anchorPage.nextKey?.minus(1)
    }

    private companion object {
        const val FIRST_PAGE = 1
    }
}
