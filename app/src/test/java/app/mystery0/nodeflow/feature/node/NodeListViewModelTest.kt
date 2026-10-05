package app.mystery0.nodeflow.feature.node

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.domain.node.GetNodePlanesUseCase
import app.mystery0.nodeflow.domain.node.NodeRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class NodeListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun refreshKeepsNodesRejectsDuplicateAndResetsStateOnFailure() = runTest(dispatcher) {
        val release = CompletableDeferred<Unit>()
        val planes = listOf(NodePlane("test", "测试分组", nodes = listOf(Node(name = "test", title = "测试节点"))))
        var requests = 0
        val repository = object : NodeRepository {
            override suspend fun nodePlanes(forceRefresh: Boolean): Result<List<NodePlane>> {
                requests++
                if (!forceRefresh) return Result.success(planes)
                release.await()
                return Result.failure(IllegalStateException("offline"))
            }
            override suspend fun node(name: String, forceRefresh: Boolean): Result<Node> = error("不应调用")
            override suspend fun topics(name: String, page: Int, forceRefresh: Boolean): Result<List<Topic>> = error("不应调用")
            override fun topicsPaging(name: String): Flow<PagingData<Topic>> = error("不应调用")
            override suspend fun blockNode(name: String): Result<Unit> = error("不应调用")
            override suspend fun clearCache() = Unit
        }
        val vm = NodeListViewModel(GetNodePlanesUseCase(repository))
        advanceUntilIdle()
        vm.onEvent(NodeListUiEvent.Refresh)
        runCurrent()
        vm.onEvent(NodeListUiEvent.Refresh)
        runCurrent()
        val pending = vm.uiState.value
        val pendingRequests = requests
        release.complete(Unit)
        advanceUntilIdle()
        assertThat(pendingRequests).isEqualTo(2)
        assertThat(pending.isRefreshing).isTrue()
        assertThat(pending.isLoading).isFalse()
        assertThat(pending.planes).isEqualTo(planes)
        assertThat(vm.uiState.value.isRefreshing).isFalse()
        assertThat(vm.uiState.value.planes).isEqualTo(planes)
        assertThat(vm.uiState.value.errorMessage).isNotEmpty()
    }
}
