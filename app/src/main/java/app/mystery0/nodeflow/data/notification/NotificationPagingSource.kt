package app.mystery0.nodeflow.data.notification

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.mystery0.nodeflow.core.model.Notification
import kotlinx.coroutines.CancellationException

class NotificationPagingSource(
    private val loadPage: suspend (page: Int) -> List<Notification>,
    private val enrichReferences: suspend (List<Notification>) -> List<Notification>,
) : PagingSource<Int, Notification>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Notification> {
        val page = params.key ?: FIRST_PAGE
        return try {
            val notifications = loadPage(page)
            val enriched = enrichReferences(notifications)
            LoadResult.Page(
                data = enriched,
                prevKey = if (page == FIRST_PAGE) null else page - 1,
                nextKey = if (notifications.size < PAGE_SIZE) null else page + 1,
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            LoadResult.Error(error)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Notification>): Int? {
        val anchorPosition = state.anchorPosition ?: return null
        val anchorPage = state.closestPageToPosition(anchorPosition) ?: return null
        return anchorPage.prevKey?.plus(1) ?: anchorPage.nextKey?.minus(1)
    }

    companion object {
        const val PAGE_SIZE = 50
        private const val FIRST_PAGE = 1
    }
}
