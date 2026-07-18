package app.mystery0.nodeflow.data.membertag

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.datastore.CachedMemberTags
import app.mystery0.nodeflow.core.model.AuthSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MemberTagRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()

    private fun repository(
        session: AuthSession = AuthSession(cookieHeader = "A2=x", username = "me"),
        cache: FakeMemberTagCache = FakeMemberTagCache(),
        fetch: suspend () -> Map<String, List<String>> = { mapOf("Alice" to listOf("大佬")) },
        update: suspend (String, List<String>, String?) -> Map<String, List<String>> =
            { username, tags, _ -> mapOf(username to tags) },
        nowEpochSeconds: () -> Long = { 10_000L },
    ) = MemberTagRepositoryImpl(
        sessionFlow = MutableStateFlow(session),
        cache = cache,
        fetchRemoteTags = fetch,
        updateRemoteTags = update,
        ioDispatcher = dispatcher,
        nowEpochSeconds = nowEpochSeconds,
    )

    @Test
    fun refresh_fetchesAndSaves() = runTest(dispatcher) {
        val cache = FakeMemberTagCache()
        val result = repository(cache = cache).refresh(force = false)

        assertThat(result.isSuccess).isTrue()
        assertThat(cache.saved?.first).containsEntry("Alice", listOf("大佬"))
        assertThat(cache.saved?.second).isEqualTo(10_000L)
    }

    @Test
    fun refresh_skipsWithinTtlUnlessForced() = runTest(dispatcher) {
        var fetchCount = 0
        val cache = FakeMemberTagCache(syncedAt = 9_500L)
        val repo = repository(cache = cache, fetch = { fetchCount++; emptyMap() })

        assertThat(repo.refresh(force = false).isSuccess).isTrue()
        assertThat(fetchCount).isEqualTo(0)

        assertThat(repo.refresh(force = true).isSuccess).isTrue()
        assertThat(fetchCount).isEqualTo(1)
    }

    @Test
    fun refresh_skipsWhenLoggedOut() = runTest(dispatcher) {
        var fetchCount = 0
        val repo = repository(
            session = AuthSession(),
            fetch = { fetchCount++; emptyMap() },
        )

        assertThat(repo.refresh(force = true).isSuccess).isTrue()
        assertThat(fetchCount).isEqualTo(0)
    }

    @Test
    fun refresh_keepsCacheOnFailure() = runTest(dispatcher) {
        val cache = FakeMemberTagCache(tags = mapOf("Old" to listOf("旧")))
        val repo = repository(
            cache = cache,
            fetch = { throw NodeFlowException(kind = NodeFlowException.Kind.Network, message = "boom") },
        )

        assertThat(repo.refresh(force = true).isFailure).isTrue()
        assertThat(cache.saved).isNull()
        assertThat(cache.cleared).isFalse()
    }

    @Test
    fun setTagsForUser_savesReturnedMapToCache() = runTest(dispatcher) {
        val cache = FakeMemberTagCache()
        val repo = repository(
            cache = cache,
            update = { _, _, _ -> mapOf("Alice" to listOf("新标签"), "bob" to listOf("后端")) },
        )

        val result = repo.setTagsForUser("Alice", listOf("新标签"), null)

        assertThat(result.isSuccess).isTrue()
        assertThat(cache.saved?.first).containsExactly(
            "Alice", listOf("新标签"),
            "bob", listOf("后端"),
        )
        assertThat(cache.saved?.second).isEqualTo(10_000L)
    }

    @Test
    fun setTagsForUser_failsFastWhenLoggedOut() = runTest(dispatcher) {
        var updateCount = 0
        val repo = repository(
            session = AuthSession(),
            update = { _, _, _ -> updateCount++; emptyMap() },
        )

        val result = repo.setTagsForUser("Alice", listOf("x"), null)

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.Auth)
        assertThat(updateCount).isEqualTo(0)
    }

    @Test
    fun setTagsForUser_keepsCacheOnRemoteFailure() = runTest(dispatcher) {
        val cache = FakeMemberTagCache(tags = mapOf("Old" to listOf("旧")))
        val repo = repository(
            cache = cache,
            update = { _, _, _ ->
                throw NodeFlowException(kind = NodeFlowException.Kind.Network, message = "boom")
            },
        )

        assertThat(repo.setTagsForUser("Alice", listOf("x"), null).isFailure).isTrue()
        assertThat(cache.saved).isNull()
    }
}

private class FakeMemberTagCache(
    tags: Map<String, List<String>> = emptyMap(),
    syncedAt: Long? = null,
) : MemberTagCache {
    private val state = MutableStateFlow(CachedMemberTags(tags, syncedAt))

    var saved: Pair<Map<String, List<String>>, Long>? = null
        private set
    var cleared: Boolean = false
        private set

    override fun observe() = state

    override suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long) {
        saved = tags to syncedAtEpochSeconds
        state.value = CachedMemberTags(tags, syncedAtEpochSeconds)
    }

    override suspend fun clear() {
        cleared = true
    }
}
