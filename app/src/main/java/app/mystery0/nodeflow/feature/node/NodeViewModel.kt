package app.mystery0.nodeflow.feature.node

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.node.BlockNodeUseCase
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.node.GetNodeUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NodeViewModel(
    savedStateHandle: SavedStateHandle,
    private val getNode: GetNodeUseCase,
    private val getNodeTopicsPaging: GetNodeTopicsPagingUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val blockNodeUseCase: BlockNodeUseCase,
    observeAuthSession: ObserveAuthSessionUseCase,
) : ViewModel() {
    private val nodeName: String = savedStateHandle["nodeName"] ?: "python"
    private val _uiState = MutableStateFlow(NodeUiState(nodeName = nodeName))
    val uiState: StateFlow<NodeUiState> = _uiState.asStateFlow()

    private val refreshRequests = MutableStateFlow(0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val topics = refreshRequests
        .flatMapLatest { getNodeTopicsPaging(nodeName) }
        .cachedIn(viewModelScope)

    init {
        observeSettings()
            .map { settings -> settings.pinnedHomeNode?.name == nodeName }
            .distinctUntilChanged()
            .onEach { isPinned ->
                _uiState.update { it.copy(isPinnedHomeNode = isPinned) }
            }
            .launchIn(viewModelScope)
        observeAuthSession()
            .map { session -> !session.cookieHeader.isNullOrBlank() }
            .distinctUntilChanged()
            .onEach { isLoggedIn ->
                _uiState.update { it.copy(isLoggedIn = isLoggedIn) }
            }
            .launchIn(viewModelScope)
        loadNodeInfo(forceRefresh = false)
    }

    fun onEvent(event: NodeUiEvent) {
        when (event) {
            NodeUiEvent.Refresh,
            NodeUiEvent.Retry,
            -> {
                loadNodeInfo(forceRefresh = true)
                refreshRequests.update { it + 1 }
            }
            NodeUiEvent.TogglePinnedHomeNode -> togglePinnedHomeNode()
            NodeUiEvent.BlockNode -> blockNode()
            NodeUiEvent.BlockNodeErrorConsumed -> {
                _uiState.update { it.copy(blockNodeError = null) }
            }
            NodeUiEvent.BlockNodeResultConsumed -> {
                _uiState.update { it.copy(blockNodeCompleted = false) }
            }
        }
    }

    private fun loadNodeInfo(forceRefresh: Boolean) {
        viewModelScope.launch {
            val node = getNode(nodeName, forceRefresh).getOrNull()
            if (node != null) {
                _uiState.update { it.copy(node = node) }
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

    private fun blockNode() {
        if (!_uiState.value.isLoggedIn || _uiState.value.isBlockingNode) return
        _uiState.update {
            it.copy(
                isBlockingNode = true,
                blockNodeError = null,
                blockNodeCompleted = false,
            )
        }
        viewModelScope.launch {
            blockNodeUseCase(nodeName)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isBlockingNode = false,
                            blockNodeCompleted = true,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isBlockingNode = false,
                            blockNodeError = error.toUserMessage(),
                        )
                    }
                }
        }
    }
}
