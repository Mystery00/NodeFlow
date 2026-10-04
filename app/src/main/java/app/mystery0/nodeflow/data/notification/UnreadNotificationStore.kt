package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.domain.notification.UnreadNotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 应用生命周期内共享的未读数量；只消费既有响应，不发请求、不持久化。 */
class UnreadNotificationStore(
    private val sessions: Flow<AuthSession>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : UnreadNotificationRepository {
    private val lock = Any()
    private var session = AuthSession()
    private var generation = 0L
    private var nextRequest = 0L
    private var lastAppliedRequest = 0L
    private val snapshot = MutableStateFlow(Snapshot())

    override val unreadCount: Flow<Int?> = combine(sessions, snapshot) { current, value ->
        value.count.takeIf { current == value.session }
    }.distinctUntilChanged()

    init {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            sessions.collect { current -> synchronized(lock) { synchronizeSession(current) } }
        }
    }

    suspend fun beginRequest(): RequestToken {
        val current = sessions.first()
        return synchronized(lock) {
            synchronizeSession(current)
            RequestToken(generation, ++nextRequest)
        }
    }

    suspend fun update(token: RequestToken?, count: Int?) {
        if (token == null || count == null || count < 0) return
        val current = sessions.first()
        synchronized(lock) {
            synchronizeSession(current)
            if (session.username.isNullOrBlank() || session.cookieHeader.isNullOrBlank()) return
            if (token.generation != generation || token.sequence < lastAppliedRequest) return
            lastAppliedRequest = token.sequence
            snapshot.value = Snapshot(session, count)
        }
    }

    private fun synchronizeSession(current: AuthSession) {
        if (session == current) return
        session = current
        generation++
        lastAppliedRequest = 0
        snapshot.value = Snapshot(current)
    }

    class RequestToken internal constructor(internal val generation: Long, internal val sequence: Long)
    private data class Snapshot(val session: AuthSession = AuthSession(), val count: Int? = null)
}
