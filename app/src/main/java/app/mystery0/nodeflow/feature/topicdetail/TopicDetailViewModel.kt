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
    private var loadGeneration: Long = 0

    init {
        load(forceRefresh = false)
    }

    fun onEvent(event: TopicDetailUiEvent) {
        when (event) {
            TopicDetailUiEvent.Refresh -> load(forceRefresh = true)
            TopicDetailUiEvent.Retry -> load(forceRefresh = true)
            is TopicDetailUiEvent.ReplyCreated -> {
                _uiState.update { it.copy(replyFloorTarget = event.floor) }
                load(forceRefresh = true)
            }
            TopicDetailUiEvent.ReplyFloorTargetConsumed ->
                _uiState.update { it.copy(replyFloorTarget = null) }
        }
    }

    private fun load(forceRefresh: Boolean) {
        val generation = ++loadGeneration
        _uiState.update {
            it.copy(
                isLoading = it.detail == null,
                isRefreshing = forceRefresh && it.detail != null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = getTopicDetail(topicId, forceRefresh)
            if (generation != loadGeneration) return@launch
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
