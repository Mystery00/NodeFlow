package app.mystery0.nodeflow.feature.home

sealed interface HomeUiEvent {
    data object Refresh : HomeUiEvent
    data object Retry : HomeUiEvent
}
