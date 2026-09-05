package app.mystery0.nodeflow.data.topic

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.mystery0.nodeflow.core.model.FavoriteTopicsPage
import app.mystery0.nodeflow.core.model.Topic
import kotlinx.coroutines.CancellationException

class FavoriteTopicsPagingSource(
    private val loadPage: suspend (Int) -> FavoriteTopicsPage,
) : PagingSource<Int, Topic>() {
    private val seenTopicIds = mutableSetOf<Long>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Topic> = try {
        val page = loadPage(params.key ?: 1)
        if (params is LoadParams.Refresh) seenTopicIds.clear()
        LoadResult.Page(
            data = page.topics.filter { seenTopicIds.add(it.id) },
            prevKey = null,
            nextKey = page.nextPage,
        )
    } catch (error: Exception) {
        if (error is CancellationException) throw error
        LoadResult.Error(error)
    }

    // 收藏新增/删除会改变分页边界，刷新从第一页重新建立列表和去重集合。
    override fun getRefreshKey(state: PagingState<Int, Topic>): Int? = null
}
