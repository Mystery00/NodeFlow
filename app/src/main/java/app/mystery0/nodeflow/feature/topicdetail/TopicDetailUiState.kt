package app.mystery0.nodeflow.feature.topicdetail

import app.mystery0.nodeflow.core.model.TopicDetail

data class TopicDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val detail: TopicDetail? = null,
    val errorMessage: String? = null,
    val replyFloorTarget: Int? = null,
    val hasMoreReplies: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null,
)
