package app.mystery0.nodeflow.feature.topicdetail

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
import app.mystery0.nodeflow.domain.topic.GetTopicDetailUseCase
import app.mystery0.nodeflow.domain.topic.TopicRepository
import com.google.common.truth.Truth.assertThat
import java.util.ArrayDeque
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
class TopicDetailViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun initialLoad_showsAccessDeniedAsFullPageState() = runTest(testDispatcher) {
        val repository = FakeTopicRepository(
            responses = ArrayDeque(
                listOf(Result.failure<TopicDetail>(accessDenied())),
            ),
        )
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.detail).isNull()
        assertThat(state.errorMessage).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
        assertThat(repository.requests).containsExactly(1221181L to false)
    }

    @Test
    fun refresh_clearsExistingDetailWhenAccessIsDenied() = runTest(testDispatcher) {
        val original = topicDetail()
        val repository = FakeTopicRepository(
            responses = ArrayDeque(
                listOf(
                    Result.success(original),
                    Result.failure<TopicDetail>(accessDenied()),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.detail).isEqualTo(original)

        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.detail).isNull()
        assertThat(state.errorMessage).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
        assertThat(repository.requests).containsExactly(
            1221181L to false,
            1221181L to true,
        ).inOrder()
    }

    @Test
    fun refresh_keepsExistingDetailWhenOtherFailureOccurs() = runTest(testDispatcher) {
        val original = topicDetail()
        val networkError = NodeFlowException(
            kind = NodeFlowException.Kind.Network,
            message = "网络连接失败，请稍后重试",
        )
        val repository = FakeTopicRepository(
            responses = ArrayDeque(
                listOf(
                    Result.success(original),
                    Result.failure<TopicDetail>(networkError),
                ),
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.detail).isEqualTo(original)
        assertThat(state.errorMessage).isEqualTo("网络连接失败，请稍后重试")
    }

    @Test
    fun replyCreated_forcesRefreshAndPublishesFloorTarget() = runTest(testDispatcher) {
        val detail = topicDetail()
        val repository = FakeTopicRepository(
            responses = ArrayDeque(listOf(Result.success(detail), Result.success(detail))),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(8))
        advanceUntilIdle()

        assertThat(repository.requests.last()).isEqualTo(1221181L to true)
        assertThat(viewModel.uiState.value.replyFloorTarget).isEqualTo(8)
        viewModel.onEvent(TopicDetailUiEvent.ReplyFloorTargetConsumed)
        assertThat(viewModel.uiState.value.replyFloorTarget).isNull()
    }

    @Test
    fun olderRefreshResult_doesNotOverwriteNewerReplyRefresh() = runTest(testDispatcher) {
        fun detail(title: String): TopicDetail {
            val source = topicDetail()
            return source.copy(topic = source.topic.copy(title = title))
        }
        val initial = CompletableDeferred(Result.success(detail("初始")))
        val older = CompletableDeferred<Result<TopicDetail>>()
        val newer = CompletableDeferred<Result<TopicDetail>>()
        val repository = DeferredTopicRepository(ArrayDeque(listOf(initial, older, newer)))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        runCurrent()
        viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(8))
        runCurrent()
        newer.complete(Result.success(detail("新回复")))
        runCurrent()
        older.complete(Result.success(detail("旧刷新")))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.detail!!.topic.title).isEqualTo("新回复")
        assertThat(viewModel.uiState.value.replyFloorTarget).isEqualTo(8)
    }

    private fun viewModel(repository: TopicRepository): TopicDetailViewModel =
        TopicDetailViewModel(
            savedStateHandle = SavedStateHandle(
                mapOf("topicId" to 1221181L),
            ),
            getTopicDetail = GetTopicDetailUseCase(repository),
        )

    private fun accessDenied(): NodeFlowException =
        NodeFlowException(
            kind = NodeFlowException.Kind.AccessDenied,
            message = V2EX_ACCESS_DENIED_MESSAGE,
        )

    private fun topicDetail(): TopicDetail =
        TopicDetail(
            topic = Topic(
                id = 1221181,
                title = "受限归档主题",
                url = "https://www.v2ex.com/t/1221181",
                node = Node(name = "flamewar", title = "水深火热"),
                author = User(username = "alice"),
                replyCount = 0,
            ),
            content = "真实正文",
            contentRendered = "<p>真实正文</p>",
            replies = emptyList(),
        )

    private class FakeTopicRepository(
        private val responses: ArrayDeque<Result<TopicDetail>>,
    ) : TopicRepository {
        val requests = mutableListOf<Pair<Long, Boolean>>()

        override suspend fun latestTopics(
            forceRefresh: Boolean,
        ): Result<List<Topic>> = Result.success(emptyList())

        override fun latestTopicsPaging(): Flow<PagingData<Topic>> =
            flowOf(PagingData.empty())

        override suspend fun topicDetail(
            topicId: Long,
            forceRefresh: Boolean,
        ): Result<TopicDetail> {
            requests += topicId to forceRefresh
            return responses.removeFirst()
        }

        override suspend fun clearCache() = Unit
    }

    private class DeferredTopicRepository(
        private val responses: ArrayDeque<CompletableDeferred<Result<TopicDetail>>>,
    ) : TopicRepository {
        override suspend fun latestTopics(forceRefresh: Boolean): Result<List<Topic>> =
            Result.success(emptyList())

        override fun latestTopicsPaging(): Flow<PagingData<Topic>> = flowOf(PagingData.empty())

        override suspend fun topicDetail(topicId: Long, forceRefresh: Boolean): Result<TopicDetail> =
            responses.removeFirst().await()

        override suspend fun clearCache() = Unit
    }
}
