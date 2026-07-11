package app.mystery0.nodeflow.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.cachedIn
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsPagingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class HomeViewModel(
    private val getLatestTopicsPaging: GetLatestTopicsPagingUseCase,
    private val getNodeTopicsPaging: GetNodeTopicsPagingUseCase,
    observeSettings: ObserveSettingsUseCase,
) : ViewModel() {
    private val refreshRequests = MutableStateFlow(0)
    private val pinnedHomeNode = observeSettings()
        .map { settings -> settings.pinnedHomeNode }
        .distinctUntilChanged()

    val uiState: StateFlow<HomeUiState> = pinnedHomeNode
        .map { pinnedNode ->
            HomeUiState(
                title = pinnedNode?.title
                    ?.takeIf { it.isNotBlank() }
                    ?: pinnedNode?.name
                        ?.takeIf { it.isNotBlank() }
                    ?: "NodeFlow",
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val topics = combine(refreshRequests, pinnedHomeNode) { _, pinnedNode -> pinnedNode }
        .flatMapLatest { pinnedNode ->
            if (pinnedNode == null) {
                getLatestTopicsPaging()
            } else {
                getNodeTopicsPaging(pinnedNode.name)
            }
        }
        .cachedIn(viewModelScope)

    fun onEvent(event: HomeUiEvent) {
        when (event) {
            HomeUiEvent.Refresh,
            HomeUiEvent.Retry,
            -> refreshRequests.update { it + 1 }
        }
    }
}
