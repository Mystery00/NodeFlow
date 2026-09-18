package app.mystery0.nodeflow.feature.topicdetail

import app.mystery0.nodeflow.core.model.ImageShareTarget
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
    val isTogglingFavorite: Boolean = false,
    val favoriteError: String? = null,
    val favoriteToastMessage: String? = null,
    val isThankingTopic: Boolean = false,
    val thankingReplyId: Long? = null,
    val thankError: String? = null,
    val isSharingImage: Boolean = false,
    val shareTarget: ImageShareTarget? = null,
    val shareError: String? = null,
)

