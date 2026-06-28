package app.mystery0.nodeflow.feature.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.domain.notification.NotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationViewModel @Inject constructor(
    private val repository: NotificationRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationUiState())
    val uiState: StateFlow<NotificationUiState> = _uiState.asStateFlow()

    fun onEvent(event: NotificationUiEvent) {
        when (event) {
            NotificationUiEvent.Refresh -> viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                val notifications = repository.notifications().getOrDefault(emptyList())
                _uiState.update { it.copy(isLoading = false, notifications = notifications) }
            }
        }
    }
}
