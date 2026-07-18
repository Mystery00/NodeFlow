package app.mystery0.nodeflow.feature.topicdetail

sealed interface TopicDetailUiEvent {
    data object Refresh : TopicDetailUiEvent
    data object Retry : TopicDetailUiEvent
    data class ReplyCreated(val floor: Int) : TopicDetailUiEvent
    data object ReplyFloorTargetConsumed : TopicDetailUiEvent
}
