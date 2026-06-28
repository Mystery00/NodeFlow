package app.mystery0.nodeflow.domain.auth

import app.mystery0.nodeflow.core.model.AuthSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<AuthSession>
    suspend fun saveSession(session: AuthSession)
    suspend fun clearSession()
}
