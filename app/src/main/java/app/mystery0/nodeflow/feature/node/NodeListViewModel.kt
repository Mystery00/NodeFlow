package app.mystery0.nodeflow.feature.node

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.node.GetNodePlanesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NodeListViewModel(
    private val getNodePlanes: GetNodePlanesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NodeListUiState())
    val uiState: StateFlow<NodeListUiState> = _uiState.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun onEvent(event: NodeListUiEvent) {
        when (event) {
            NodeListUiEvent.Refresh -> load(forceRefresh = true)
            NodeListUiEvent.Retry -> load(forceRefresh = true)
            is NodeListUiEvent.QueryChanged -> {
                _uiState.update { it.copy(query = event.query) }
            }
        }
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = it.planes.isEmpty(),
                    isRefreshing = forceRefresh && it.planes.isNotEmpty(),
                    errorMessage = null,
                )
            }
            val result = getNodePlanes(forceRefresh)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { planes ->
                        current.copy(
                            isLoading = false,
                            isRefreshing = false,
                            planes = planes,
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
