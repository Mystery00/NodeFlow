package app.mystery0.nodeflow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val getLatestTopics: GetLatestTopicsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.Refresh -> load(forceRefresh = true)
            HomeUiEvent.Retry -> load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.topics.isEmpty(),
                    isRefreshing = forceRefresh && it.topics.isNotEmpty(),
                    errorMessage = null,
                )
            }
            val result = getLatestTopics(forceRefresh)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { topics ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            topics = topics,
                            errorMessage = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = error.toUserMessage(),
                        )
                    },
                )
            }
        }
    }
}
