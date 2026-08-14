package app.mystery0.nodeflow.feature.node

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.ThemeMode
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.node.BlockNodeUseCase
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.node.GetNodeUseCase
import app.mystery0.nodeflow.domain.node.NodeRepository
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NodeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun authSession_updatesLoggedInState() = runTest(dispatcher) {
        val session = MutableStateFlow(AuthSession(username = "alice", cookieHeader = "cookie"))
        val viewModel = viewModel(session = session)

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoggedIn).isTrue()

        session.value = AuthSession()
        runCurrent()
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
    }

    @Test
    fun blockNode_successPublishesLoadingAndCompletedState() = runTest(dispatcher) {
        val repository = FakeNodeRepository()
        val viewModel = viewModel(nodeRepository = repository)
        advanceUntilIdle()

        viewModel.onEvent(NodeUiEvent.BlockNode)
        runCurrent()

        assertThat(viewModel.uiState.value.isBlockingNode).isTrue()
        assertThat(repository.blockedNames).containsExactly("android")

        repository.blockResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isBlockingNode).isFalse()
        assertThat(viewModel.uiState.value.blockNodeCompleted).isTrue()

        viewModel.onEvent(NodeUiEvent.BlockNodeResultConsumed)
        assertThat(viewModel.uiState.value.blockNodeCompleted).isFalse()
    }

    @Test
    fun blockNode_failurePublishesAndConsumesError() = runTest(dispatcher) {
        val repository = FakeNodeRepository()
        val viewModel = viewModel(nodeRepository = repository)
        advanceUntilIdle()
        repository.blockResult.complete(
            Result.failure(
                NodeFlowException(
                    kind = NodeFlowException.Kind.Network,
                    message = "网络连接失败，请稍后重试",
                ),
            ),
        )

        viewModel.onEvent(NodeUiEvent.BlockNode)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isBlockingNode).isFalse()
        assertThat(viewModel.uiState.value.blockNodeError).isEqualTo("网络连接失败，请稍后重试")

        viewModel.onEvent(NodeUiEvent.BlockNodeErrorConsumed)
        assertThat(viewModel.uiState.value.blockNodeError).isNull()
    }

    @Test
    fun blockNode_doesNotCallUseCaseWhenLoggedOut() = runTest(dispatcher) {
        val repository = FakeNodeRepository()
        val viewModel = viewModel(
            nodeRepository = repository,
            session = MutableStateFlow(AuthSession()),
        )
        advanceUntilIdle()

        viewModel.onEvent(NodeUiEvent.BlockNode)
        runCurrent()

        assertThat(repository.blockedNames).isEmpty()
        assertThat(viewModel.uiState.value.isBlockingNode).isFalse()
    }

    private fun viewModel(
        nodeRepository: FakeNodeRepository = FakeNodeRepository(),
        session: MutableStateFlow<AuthSession> = MutableStateFlow(
            AuthSession(username = "alice", cookieHeader = "cookie"),
        ),
    ): NodeViewModel {
        val settingsRepository = FakeSettingsRepository()
        return NodeViewModel(
            savedStateHandle = SavedStateHandle(mapOf("nodeName" to "android")),
            getNode = GetNodeUseCase(nodeRepository),
            getNodeTopicsPaging = GetNodeTopicsPagingUseCase(nodeRepository),
            observeSettings = ObserveSettingsUseCase(settingsRepository),
            updateSettings = UpdateSettingsUseCase(settingsRepository),
            blockNodeUseCase = BlockNodeUseCase(nodeRepository),
            observeAuthSession = ObserveAuthSessionUseCase(FakeAuthRepository(session)),
        )
    }

    private class FakeNodeRepository : NodeRepository {
        val blockedNames = mutableListOf<String>()
        val blockResult = CompletableDeferred<Result<Unit>>()

        override suspend fun node(name: String, forceRefresh: Boolean): Result<Node> =
            Result.success(Node(id = 39L, name = name, title = "Android"))

        override suspend fun blockNode(name: String): Result<Unit> {
            blockedNames += name
            return blockResult.await()
        }

        override suspend fun nodePlanes(forceRefresh: Boolean): Result<List<NodePlane>> =
            Result.success(emptyList())

        override suspend fun topics(
            name: String,
            page: Int,
            forceRefresh: Boolean,
        ): Result<List<Topic>> = Result.success(emptyList())

        override fun topicsPaging(name: String): Flow<PagingData<Topic>> =
            flowOf(PagingData.empty())

        override suspend fun clearCache() = Unit
    }

    private class FakeSettingsRepository : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun setThemeMode(themeMode: ThemeMode) = Unit
        override suspend fun setDynamicColor(enabled: Boolean) = Unit
        override suspend fun setPinnedHomeNode(node: PinnedHomeNode?) = Unit
        override suspend fun setCustomImageHosts(hosts: List<String>) = Unit
        override suspend fun setShowMemberTags(enabled: Boolean) = Unit
        override suspend fun setNotificationReminder(enabled: Boolean) = Unit
        override suspend fun clearCache() = Unit
    }

    private class FakeAuthRepository(
        override val session: Flow<AuthSession>,
    ) : AuthRepository {
        override suspend fun loginChallenge(): Result<LoginChallenge> =
            Result.failure(AssertionError("Unexpected login challenge"))

        override suspend fun login(
            username: String,
            password: String,
            captcha: String,
            challenge: LoginChallenge,
        ): Result<AuthLoginResult> = Result.failure(AssertionError("Unexpected login"))

        override suspend fun verifyTwoFactor(
            code: String,
            challenge: TwoFactorChallenge,
        ): Result<AuthSession> = Result.failure(AssertionError("Unexpected verification"))

        override suspend fun saveSession(session: AuthSession) = Unit
        override suspend fun clearSession() = Unit
    }
}
