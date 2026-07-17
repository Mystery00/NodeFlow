package app.mystery0.nodeflow.data.membertag

import app.mystery0.nodeflow.core.datastore.CachedMemberTags
import app.mystery0.nodeflow.core.datastore.MemberTagStore
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.domain.membertag.MemberTagRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** MemberTagStore 的抽象，便于单测替换。 */
interface MemberTagCache {
    fun observe(): Flow<CachedMemberTags>
    suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long)
    suspend fun clear()
}

class DataStoreMemberTagCache(private val store: MemberTagStore) : MemberTagCache {
    override fun observe(): Flow<CachedMemberTags> = store.observe()
    override suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long) =
        store.save(tags, syncedAtEpochSeconds)
    override suspend fun clear() = store.clear()
}

class MemberTagRepositoryImpl(
    private val sessionFlow: Flow<AuthSession>,
    private val cache: MemberTagCache,
    private val fetchRemoteTags: suspend () -> Map<String, List<String>>,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : MemberTagRepository {
    override fun observeTags(): Flow<Map<String, List<String>>> =
        cache.observe().map { it.tags }

    override fun observeSyncedAt(): Flow<Long?> =
        cache.observe().map { it.syncedAtEpochSeconds }

    override suspend fun refresh(force: Boolean): Result<Unit> = withContext(ioDispatcher) {
        val session = sessionFlow.first()
        val loggedIn = !session.cookieHeader.isNullOrBlank() ||
            !session.personalAccessToken.isNullOrBlank()
        if (!loggedIn) return@withContext Result.success(Unit)
        if (!force) {
            val syncedAt = cache.observe().first().syncedAtEpochSeconds
            if (syncedAt != null && nowEpochSeconds() - syncedAt < REFRESH_TTL_SECONDS) {
                return@withContext Result.success(Unit)
            }
        }
        runCatching {
            val tags = fetchRemoteTags()
            cache.save(tags, nowEpochSeconds())
        }
    }

    private companion object {
        const val REFRESH_TTL_SECONDS = 60L * 60L
    }
}
