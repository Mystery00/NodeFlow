package app.mystery0.nodeflow.feature.favorites

sealed interface FavoriteTopicsUiEvent {
    data object Refresh : FavoriteTopicsUiEvent
    data object Resume : FavoriteTopicsUiEvent
}
