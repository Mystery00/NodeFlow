package app.mystery0.nodeflow.feature.node

sealed interface NodeUiEvent {
    data object Refresh : NodeUiEvent
    data object Retry : NodeUiEvent
    data object TogglePinnedHomeNode : NodeUiEvent
}
