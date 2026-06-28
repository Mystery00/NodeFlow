package app.mystery0.nodeflow.feature.node

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.node.GetNodeTopicsUseCase
import app.mystery0.nodeflow.domain.node.GetNodeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class NodeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getNode: GetNodeUseCase,
    private val getNodeTopics: GetNodeTopicsUseCase,
) : ViewModel() {
    private val nodeName: String = savedStateHandle["nodeName"] ?: "python"
    private val _uiState = MutableStateFlow(NodeUiState(nodeName = nodeName))
    val uiState: StateFlow<NodeUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun onEvent(event: NodeUiEvent) {
        when (event) {
            NodeUiEvent.Refresh -> load(forceRefresh = true)
            NodeUiEvent.Retry -> load(forceRefresh = true)
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
            val nodeResult = async { getNode(nodeName, forceRefresh) }
            val topicsResult = async { getNodeTopics(nodeName, forceRefresh = forceRefresh) }
            val node = nodeResult.await().getOrNull()
            val topics = topicsResult.await()
            _uiState.update { current ->
                topics.fold(
                    onSuccess = { list ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            node = node ?: current.node,
                            topics = list,
                            errorMessage = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            node = node ?: current.node,
                            errorMessage = error.toUserMessage(),
                        )
                    },
                )
            }
        }
    }
}
