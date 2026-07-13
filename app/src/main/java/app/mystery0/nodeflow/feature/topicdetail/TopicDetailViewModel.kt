package app.mystery0.nodeflow.feature.topicdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.isAccessDenied
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.topic.GetTopicDetailUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TopicDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val getTopicDetail: GetTopicDetailUseCase,
) : ViewModel() {
    private val topicId: Long = checkNotNull(savedStateHandle["topicId"])
    private val _uiState = MutableStateFlow(TopicDetailUiState())
    val uiState: StateFlow<TopicDetailUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun onEvent(event: TopicDetailUiEvent) {
        when (event) {
            TopicDetailUiEvent.Refresh -> load(forceRefresh = true)
            TopicDetailUiEvent.Retry -> load(forceRefresh = true)
        }
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.detail == null,
                    isRefreshing = forceRefresh && it.detail != null,
                    errorMessage = null,
                )
            }
            val result = getTopicDetail(topicId, forceRefresh)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { detail ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            detail = detail,
                            errorMessage = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            detail = if (error.isAccessDenied()) null else current.detail,
                            errorMessage = error.toUserMessage(),
                        )
                    },
                )
            }
        }
    }
}
