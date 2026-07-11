package app.mystery0.nodeflow.domain.user

import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.model.UserRecentActivity

interface UserRepository {
    suspend fun user(username: String, forceRefresh: Boolean = false): Result<User>
    suspend fun recentActivity(username: String): Result<UserRecentActivity>
    suspend fun clearCache()
}
