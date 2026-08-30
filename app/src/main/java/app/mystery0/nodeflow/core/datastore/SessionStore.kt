package app.mystery0.nodeflow.core.datastore

import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SessionStore(
    private val storage: EncryptedKeyValueStore,
) {
    private val _session = MutableStateFlow(readSession())
    private val mutationMutex = Mutex()
    val session: Flow<AuthSession> = _session.asStateFlow()

    suspend fun save(session: AuthSession) = mutationMutex.withLock {
        if (session == AuthSession()) {
            storage.remove(SESSION_KEY)
        } else {
            storage.write(SESSION_KEY, SessionJson.encodeToString(session.toPersisted()))
        }
        _session.value = session
    }

    suspend fun clear() = mutationMutex.withLock {
        storage.remove(SESSION_KEY)
        _session.value = AuthSession()
    }

    private fun readSession(): AuthSession {
        val raw = storage.read(SESSION_KEY) ?: return AuthSession()
        return try {
            SessionJson.decodeFromString<PersistedAuthSession>(raw).toAuthSession()
        } catch (_: Exception) {
            storage.remove(SESSION_KEY)
            AuthSession()
        }
    }

    private companion object {
        const val SESSION_KEY = "auth_session"
        val SessionJson = Json {
            ignoreUnknownKeys = true
        }
    }
}

@Serializable
private data class PersistedAuthSession(
    val personalAccessToken: String? = null,
    val cookieHeader: String? = null,
    val username: String? = null,
)

private fun AuthSession.toPersisted() = PersistedAuthSession(
    personalAccessToken = personalAccessToken,
    cookieHeader = cookieHeader,
    username = username,
)

private fun PersistedAuthSession.toAuthSession() = AuthSession(
    personalAccessToken = personalAccessToken,
    cookieHeader = cookieHeader,
    username = username,
)
