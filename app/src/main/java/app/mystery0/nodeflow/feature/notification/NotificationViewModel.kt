package app.mystery0.nodeflow.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import app.mystery0.nodeflow.domain.notification.NotificationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

class NotificationViewModel(
    private val repository: NotificationRepository,
) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val notifications = refreshRequests
        .flatMapLatest { repository.notificationsPaging() }
        .cachedIn(viewModelScope)

    fun onEvent(event: NotificationUiEvent) {
        when (event) {
            NotificationUiEvent.Refresh -> refreshRequests.update { it + 1 }
        }
    }
}
