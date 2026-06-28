package app.mystery0.nodeflow.data.auth

import app.mystery0.nodeflow.core.datastore.SessionStore
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.domain.auth.AuthRepository
import kotlinx.coroutines.flow.Flow

class AuthRepositoryImpl(
    private val sessionStore: SessionStore,
) : AuthRepository {
    override val session: Flow<AuthSession> = sessionStore.session

    override suspend fun saveSession(session: AuthSession) {
        sessionStore.save(session)
    }

    override suspend fun clearSession() {
        sessionStore.clear()
    }
}
