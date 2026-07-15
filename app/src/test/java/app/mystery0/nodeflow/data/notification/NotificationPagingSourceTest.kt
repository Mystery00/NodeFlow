package app.mystery0.nodeflow.data.notification

import androidx.paging.PagingSource
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationPagingSourceTest {
    @Test
    fun refresh_loadsFirstPageAndProvidesNextKeyForFullPage() = runTest {
        val source = NotificationPagingSource(
            loadPage = { page -> List(50) { index -> notification(page * 100L + index) } },
            enrichReferences = { it },
        )

        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page<Int, Notification>

        assertThat(result.data).hasSize(50)
        assertThat(result.prevKey).isNull()
        assertThat(result.nextKey).isEqualTo(2)
    }

    @Test
    fun append_stopsAtPartialLastPage() = runTest {
        val source = NotificationPagingSource(
            loadPage = { listOf(notification(1)) },
            enrichReferences = { it },
        )

        val result = source.load(
            PagingSource.LoadParams.Append(key = 3, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page<Int, Notification>

        assertThat(result.prevKey).isEqualTo(2)
        assertThat(result.nextKey).isNull()
    }

    @Test
    fun load_enrichesReferencesWithoutDroppingNotifications() = runTest {
        val source = NotificationPagingSource(
            loadPage = { listOf(notification(1)) },
            enrichReferences = { notifications -> notifications.map { it.copy(action = "已补全") } },
        )

        val result = source.load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 50, placeholdersEnabled = false),
        ) as PagingSource.LoadResult.Page<Int, Notification>

        assertThat(result.data.single().action).isEqualTo("已补全")
    }

    private fun notification(id: Long) = Notification(
        id = id,
        actor = User(username = "actor$id"),
        action = "回复了你",
        topicId = id,
        topicTitle = "测试主题",
        relativeTime = "刚刚",
    )
}
