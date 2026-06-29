package app.mystery0.nodeflow.feature.node

sealed interface NodeListUiEvent {
    data object Refresh : NodeListUiEvent
    data object Retry : NodeListUiEvent
    data class QueryChanged(val query: String) : NodeListUiEvent
}
