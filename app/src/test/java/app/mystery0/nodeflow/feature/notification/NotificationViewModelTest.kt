package app.mystery0.nodeflow.feature.notification

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.domain.notification.NotificationRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun notifications_initialLoadAndRefreshResubscribePaging() = runTest(testDispatcher) {
        val repository = FakeNotificationRepository()
        val viewModel = NotificationViewModel(repository)
        val collectJob = launch { viewModel.notifications.collect {} }

        advanceUntilIdle()
        assertThat(repository.pagingCalls).isEqualTo(1)

        viewModel.onEvent(NotificationUiEvent.Refresh)
        advanceUntilIdle()
        assertThat(repository.pagingCalls).isEqualTo(2)

        collectJob.cancel()
    }

    private class FakeNotificationRepository : NotificationRepository {
        var pagingCalls = 0
            private set

        override fun notificationsPaging(): Flow<PagingData<Notification>> {
            pagingCalls += 1
            return flowOf(PagingData.empty())
        }
    }
}
