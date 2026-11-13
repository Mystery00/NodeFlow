package app.mystery0.nodeflow.feature.node

import androidx.lifecycle.SavedStateHandle
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.node.BlockNodeUseCase
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.node.GetNodeUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import app.mystery0.nodeflow.testing.FakeAuthRepository
import app.mystery0.nodeflow.testing.FakeNodeRepository
import app.mystery0.nodeflow.testing.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val auth = FakeAuthRepository(AuthSession(username = "alice", cookieHeader = "cookie"))
        val viewModel = viewModel(auth = auth)

        advanceUntilIdle()
        assertThat(viewModel.uiState.value.isLoggedIn).isTrue()

        auth.sessionState.value = AuthSession()
        runCurrent()
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
    }

    @Test
    fun blockNode_successPublishesLoadingAndCompletedState() = runTest(dispatcher) {
        val blockResult = CompletableDeferred<Result<Unit>>()
        val repository = fakeNodeRepository(blockResult)
        val viewModel = viewModel(nodeRepository = repository)
        advanceUntilIdle()

        viewModel.onEvent(NodeUiEvent.BlockNode)
        runCurrent()

        assertThat(viewModel.uiState.value.isBlockingNode).isTrue()
        assertThat(repository.blockedNames).containsExactly("android")

        blockResult.complete(Result.success(Unit))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isBlockingNode).isFalse()
        assertThat(viewModel.uiState.value.blockNodeCompleted).isTrue()

        viewModel.onEvent(NodeUiEvent.BlockNodeResultConsumed)
        assertThat(viewModel.uiState.value.blockNodeCompleted).isFalse()
    }

    @Test
    fun blockNode_failurePublishesAndConsumesError() = runTest(dispatcher) {
        val blockResult = CompletableDeferred<Result<Unit>>()
        val repository = fakeNodeRepository(blockResult)
        val viewModel = viewModel(nodeRepository = repository)
        advanceUntilIdle()
        blockResult.complete(
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
        val repository = fakeNodeRepository()
        val viewModel = viewModel(
            nodeRepository = repository,
            auth = FakeAuthRepository(AuthSession()),
        )
        advanceUntilIdle()

        viewModel.onEvent(NodeUiEvent.BlockNode)
        runCurrent()

        assertThat(repository.blockedNames).isEmpty()
        assertThat(viewModel.uiState.value.isBlockingNode).isFalse()
    }

    private fun fakeNodeRepository(blockResult: CompletableDeferred<Result<Unit>> = CompletableDeferred()) =
        object : FakeNodeRepository() {
            init {
                nodeResult = { name, _ -> Result.success(Node(id = 39L, name = name, title = "Android")) }
            }

            override suspend fun blockNode(name: String): Result<Unit> {
                blockedNames += name
                return blockResult.await()
            }
        }

    private fun viewModel(
        nodeRepository: FakeNodeRepository = fakeNodeRepository(),
        auth: FakeAuthRepository = FakeAuthRepository(AuthSession(username = "alice", cookieHeader = "cookie")),
    ): NodeViewModel {
        val settingsRepository = FakeSettingsRepository()
        return NodeViewModel(
            savedStateHandle = SavedStateHandle(mapOf("nodeName" to "android")),
            getNode = GetNodeUseCase(nodeRepository),
            getNodeTopicsPaging = GetNodeTopicsPagingUseCase(nodeRepository),
            observeSettings = ObserveSettingsUseCase(settingsRepository),
            updateSettings = UpdateSettingsUseCase(settingsRepository),
            blockNodeUseCase = BlockNodeUseCase(nodeRepository),
            observeAuthSession = ObserveAuthSessionUseCase(auth),
        )
    }
}
