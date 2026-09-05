package app.mystery0.nodeflow.feature.favorites

import androidx.lifecycle.ViewModelStore
import androidx.paging.PagingData
import androidx.paging.AsyncPagingDataDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.topic.FavoriteTopicsRepository
import app.mystery0.nodeflow.domain.topic.GetFavoriteTopicsPagingUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteTopicsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { store.clear(); Dispatchers.resetMain() }

    @Test
    fun changingAccountClearsVisibleTopicsAndCancelsOldGenerationBeforeNewResponse() = runTest(dispatcher) {
        val auth = FakeAuthRepository().apply { session.value = AuthSession(username = "test-a", cookieHeader = "test-session") }
        val newAccountResponse = CompletableDeferred<Unit>()
        var generation = 0
        var oldGenerationCancelled = false
        val favorites = FakeFavoritesRepository {
            val current = ++generation
            flow {
                if (current == 1) {
                    try {
                        emit(PagingData.from(listOf(topic(1))))
                        awaitCancellation()
                    } finally { oldGenerationCancelled = true }
                } else {
                    newAccountResponse.await()
                    emit(PagingData.from(listOf(topic(2))))
                }
            }
        }
        val vm = create(auth, favorites)
        val differ = AsyncPagingDataDiffer(
            diffCallback = object : DiffUtil.ItemCallback<Topic>() {
                override fun areItemsTheSame(oldItem: Topic, newItem: Topic) = oldItem.id == newItem.id
                override fun areContentsTheSame(oldItem: Topic, newItem: Topic) = oldItem == newItem
            },
            updateCallback = object : ListUpdateCallback {
                override fun onInserted(position: Int, count: Int) = Unit
                override fun onRemoved(position: Int, count: Int) = Unit
                override fun onMoved(fromPosition: Int, toPosition: Int) = Unit
                override fun onChanged(position: Int, count: Int, payload: Any?) = Unit
            },
            mainDispatcher = dispatcher,
            workerDispatcher = dispatcher,
        )
        val job = launch { vm.topics.collectLatest { differ.submitData(it) } }
        advanceUntilIdle()
        assertThat(differ.snapshot().items.map { it.id }).containsExactly(1L)
        auth.session.value = AuthSession(username = "test-b", cookieHeader = "test-session-b")
        advanceUntilIdle()
        assertThat(oldGenerationCancelled).isTrue()
        assertThat(differ.snapshot().items).isEmpty()
        newAccountResponse.complete(Unit)
        advanceUntilIdle()
        assertThat(differ.snapshot().items.map { it.id }).containsExactly(2L)
        auth.session.value = AuthSession()
        advanceUntilIdle()
        assertThat(differ.snapshot().items).isEmpty()
        job.cancel()
    }

    private fun topic(id: Long) = Topic(
        id, "测试主题", "https://www.v2ex.com/t/$id", Node(name = "android", title = "Android"), User(username = "tester"),
    )

    @Test
    fun onlyLoadsForLoggedInAccountAndClearsOnLogout() = runTest(dispatcher) {
        val auth = FakeAuthRepository()
        val favorites = FakeFavoritesRepository()
        val vm = create(auth, favorites)
        var emissions = 0
        val job = launch { vm.topics.collect { emissions++ } }
        advanceUntilIdle()
        assertThat(favorites.requests).isEqualTo(0)
        auth.session.value = AuthSession(username = "test-a", cookieHeader = "test-session")
        advanceUntilIdle()
        assertThat(favorites.requests).isEqualTo(1)
        assertThat(vm.uiState.value.isLoggedIn).isTrue()
        val beforeLogout = emissions
        auth.session.value = AuthSession()
        advanceUntilIdle()
        assertThat(vm.uiState.value.isLoggedIn).isFalse()
        assertThat(emissions).isGreaterThan(beforeLogout)
        vm.onEvent(FavoriteTopicsUiEvent.Refresh)
        advanceUntilIdle()
        assertThat(favorites.requests).isEqualTo(1)
        auth.session.value = AuthSession(username = "test-b", cookieHeader = "test-session-b")
        advanceUntilIdle()
        assertThat(favorites.requests).isEqualTo(2)
        job.cancel()
    }

    @Test
    fun refreshesAfterReturningFromDetailsButNotOnFirstResume() = runTest(dispatcher) {
        val auth = FakeAuthRepository().apply { session.value = AuthSession(username = "tester", cookieHeader = "test-session") }
        val favorites = FakeFavoritesRepository()
        val vm = create(auth, favorites)
        val job = launch { vm.topics.collect { } }
        vm.onEvent(FavoriteTopicsUiEvent.Resume)
        advanceUntilIdle()
        assertThat(favorites.requests).isEqualTo(1)
        vm.onEvent(FavoriteTopicsUiEvent.Resume)
        advanceUntilIdle()
        assertThat(favorites.requests).isEqualTo(2)
        vm.onEvent(FavoriteTopicsUiEvent.Refresh)
        advanceUntilIdle()
        assertThat(favorites.requests).isEqualTo(3)
        job.cancel()
    }

    private fun create(auth: FakeAuthRepository, favorites: FakeFavoritesRepository) = FavoriteTopicsViewModel(
        ObserveAuthSessionUseCase(auth), GetFavoriteTopicsPagingUseCase(favorites),
    ).also { store.put("favorites", it) }

    private class FakeFavoritesRepository(
        val pageFlow: () -> Flow<PagingData<Topic>> = { flowOf(PagingData.empty()) },
    ) : FavoriteTopicsRepository {
        var requests = 0
        override fun favoriteTopicsPaging() = pageFlow().also { requests++ }
    }

    private class FakeAuthRepository : AuthRepository {
        override val session = MutableStateFlow(AuthSession())
        override suspend fun loginChallenge(): Result<LoginChallenge> = error("Unused")
        override suspend fun login(username: String, password: String, captcha: String, challenge: LoginChallenge): Result<AuthLoginResult> = error("Unused")
        override suspend fun verifyTwoFactor(code: String, challenge: TwoFactorChallenge): Result<AuthSession> = error("Unused")
        override suspend fun saveSession(session: AuthSession) { this.session.value = session }
        override suspend fun clearSession() { session.value = AuthSession() }
    }
}
