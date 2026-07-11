package app.mystery0.nodeflow.feature.node

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.domain.node.GetNodeTopicsUseCase
import app.mystery0.nodeflow.domain.node.GetNodeUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NodeViewModel(
    savedStateHandle: SavedStateHandle,
    private val getNode: GetNodeUseCase,
    private val getNodeTopics: GetNodeTopicsUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
) : ViewModel() {
    private val nodeName: String = savedStateHandle["nodeName"] ?: "python"
    private val _uiState = MutableStateFlow(NodeUiState(nodeName = nodeName))
    val uiState: StateFlow<NodeUiState> = _uiState.asStateFlow()

    init {
        observeSettings()
            .map { settings -> settings.pinnedHomeNode?.name == nodeName }
            .distinctUntilChanged()
            .onEach { isPinned ->
                _uiState.update { it.copy(isPinnedHomeNode = isPinned) }
            }
            .launchIn(viewModelScope)
        load(forceRefresh = false)
    }

    fun onEvent(event: NodeUiEvent) {
        when (event) {
            NodeUiEvent.Refresh -> load(forceRefresh = true)
            NodeUiEvent.Retry -> load(forceRefresh = true)
            NodeUiEvent.TogglePinnedHomeNode -> togglePinnedHomeNode()
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

    private fun togglePinnedHomeNode() {
        viewModelScope.launch {
            val currentState = _uiState.value
            val node = currentState.node
            val pinnedNode = if (currentState.isPinnedHomeNode) {
                null
            } else {
                PinnedHomeNode(
                    name = node?.name?.takeIf { it.isNotBlank() } ?: nodeName,
                    title = node?.title?.takeIf { it.isNotBlank() } ?: nodeName,
                    avatarUrl = node?.avatarUrl?.takeIf { it.isNotBlank() },
                )
            }
            updateSettings.setPinnedHomeNode(pinnedNode)
        }
    }
}
