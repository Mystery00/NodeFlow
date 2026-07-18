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
    /** 该用户在未过滤缓存中的标签，供编辑对话框初始值，不受展示开关影响。 */
    val editableMemberTags: List<String> = emptyList(),
    val isTagDialogVisible: Boolean = false,
    val isSavingTags: Boolean = false,
    val tagEditError: String? = null,
)
