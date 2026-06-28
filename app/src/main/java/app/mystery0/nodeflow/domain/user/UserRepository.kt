package app.mystery0.nodeflow.domain.user

import app.mystery0.nodeflow.core.model.User

interface UserRepository {
    suspend fun user(username: String, forceRefresh: Boolean = false): Result<User>
    suspend fun clearCache()
}
