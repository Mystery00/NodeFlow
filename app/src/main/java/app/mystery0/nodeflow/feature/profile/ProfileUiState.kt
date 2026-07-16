package app.mystery0.nodeflow.feature.profile

import app.mystery0.nodeflow.core.model.ProfileReply
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User

data class ProfileUiState(
    val username: String = "",
    val isLoading: Boolean = true,
    val user: User? = null,
    val recentTopics: List<Topic> = emptyList(),
    val recentReplies: List<ProfileReply> = emptyList(),
    val errorMessage: String? = null,
    val userNotFound: Boolean = false,
)
