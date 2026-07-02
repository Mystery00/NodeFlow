package app.mystery0.nodeflow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsPagingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update

class HomeViewModel(
    private val getLatestTopicsPaging: GetLatestTopicsPagingUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val refreshRequests = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topics = refreshRequests
        .flatMapLatest { getLatestTopicsPaging() }
        .cachedIn(viewModelScope)

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.Refresh,
            HomeUiEvent.Retry,
            -> refreshRequests.update { it + 1 }
        }
    }
}
