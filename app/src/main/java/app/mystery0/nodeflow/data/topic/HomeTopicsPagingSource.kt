package app.mystery0.nodeflow.data.topic

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.mystery0.nodeflow.core.model.Topic
import kotlinx.coroutines.CancellationException

class HomeTopicsPagingSource(
    private val loadTopics: suspend (page: Int) -> List<Topic>,
    private val cacheTopics: suspend (List<Topic>) -> Unit,
) : PagingSource<Int, Topic>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Topic> {
        val page = params.key ?: FIRST_PAGE
        return try {
            val topics = loadTopics(page)
            if (topics.isNotEmpty()) {
                cacheTopics(topics)
            }
            LoadResult.Page(
                data = topics,
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
