package app.mystery0.nodeflow.feature.topicdetail

sealed interface TopicDetailUiEvent {
    data object Refresh : TopicDetailUiEvent
    data object Retry : TopicDetailUiEvent
    data object LoadMoreReplies : TopicDetailUiEvent
    data class ReplyCreated(val floor: Int) : TopicDetailUiEvent
    data object ReplyFloorTargetConsumed : TopicDetailUiEvent
    data object ToggleFavorite : TopicDetailUiEvent
    data object FavoriteErrorConsumed : TopicDetailUiEvent
}
