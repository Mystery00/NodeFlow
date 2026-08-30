package app.mystery0.nodeflow.feature.topicdetail

import androidx.lifecycle.SavedStateHandle
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.ThankResult
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
import app.mystery0.nodeflow.domain.topic.GetTopicDetailUseCase
import app.mystery0.nodeflow.domain.topic.TopicDetailPager
import app.mystery0.nodeflow.domain.topic.TopicDetailSnapshot
import app.mystery0.nodeflow.domain.topic.TopicRepository
import androidx.paging.PagingData
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
    fun initialLoad_mapsSnapshotAndOnlyCallsLoadFirst() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        val viewModel = viewModel(pager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.detail?.replies).hasSize(100)
        assertThat(state.hasMoreReplies).isTrue()
        assertThat(pager.calls).containsExactly("loadFirst(false)")
    }

    @Test
    fun initialLoad_showsAccessDeniedAsFullPageState() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.failure(accessDenied())
        val viewModel = viewModel(pager)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.isRefreshing).isFalse()
        assertThat(state.detail).isNull()
        assertThat(state.errorMessage).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
    }

    @Test
    fun deepLinkFloor_catchesUpWhenFloorNotLoaded() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.loadUntilFloorResults +=
            Result.success(snapshot(replies = replies(1..250), hasMore = false))
        val viewModel = viewModel(pager, replyFloor = 250)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(pager.calls).containsExactly("loadFirst(false)", "loadUntilFloor(250)").inOrder()
        assertThat(state.detail?.replies).hasSize(250)
        assertThat(state.hasMoreReplies).isFalse()
        // 深链定位由界面按 initialReplyFloor 执行，不设置 replyFloorTarget
        assertThat(state.replyFloorTarget).isNull()
    }

    @Test
    fun deepLinkFloor_skipsCatchUpWhenFloorAlreadyLoaded() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        val viewModel = viewModel(pager, replyFloor = 50)

        advanceUntilIdle()

        assertThat(pager.calls).containsExactly("loadFirst(false)")
        assertThat(viewModel.uiState.value.detail?.replies).hasSize(100)
    }

    @Test
    fun loadMore_appendsNextPageAndTogglesLoadingFlag() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        val next = CompletableDeferred<Result<TopicDetailSnapshot>>()
        pager.deferredLoadNext += next
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.LoadMoreReplies)
        runCurrent()
        assertThat(viewModel.uiState.value.isLoadingMore).isTrue()

        next.complete(Result.success(snapshot(replies = replies(1..200), hasMore = false)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.detail?.replies).hasSize(200)
        assertThat(state.hasMoreReplies).isFalse()
    }

    @Test
    fun loadMore_failureWritesLoadMoreErrorAndKeepsDetail() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.loadNextResults += Result.failure(networkError())
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.LoadMoreReplies)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoadingMore).isFalse()
        assertThat(state.loadMoreError).isEqualTo("网络连接失败，请稍后重试")
        assertThat(state.detail?.replies).hasSize(100)
        assertThat(state.errorMessage).isNull()
    }

    @Test
    fun loadMore_ignoredWhenNoMoreReplies() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..3), hasMore = false))
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.LoadMoreReplies)
        advanceUntilIdle()

        assertThat(pager.calls).containsExactly("loadFirst(false)")
    }

    @Test
    fun refresh_reloadsLoadedPagesAndKeepsScrollData() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.refreshResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        advanceUntilIdle()

        assertThat(pager.calls).containsExactly("loadFirst(false)", "refreshLoaded()").inOrder()
        assertThat(viewModel.uiState.value.detail?.replies).hasSize(100)
    }

    @Test
    fun refresh_keepsExistingDetailWhenOtherFailureOccurs() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.refreshResults += Result.failure(networkError())
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.detail?.replies).hasSize(100)
        assertThat(state.errorMessage).isEqualTo("网络连接失败，请稍后重试")
    }

    @Test
    fun refresh_clearsExistingDetailWhenAccessIsDenied() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.refreshResults += Result.failure(accessDenied())
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.detail).isNull()
        assertThat(state.hasMoreReplies).isFalse()
        assertThat(state.errorMessage).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
    }

    @Test
    fun retry_withoutDetailForcesFirstLoad() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.failure(networkError())
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.Retry)
        advanceUntilIdle()

        assertThat(pager.calls).containsExactly("loadFirst(false)", "loadFirst(true)").inOrder()
        assertThat(viewModel.uiState.value.detail?.replies).hasSize(100)
    }

    @Test
    fun replyCreated_refreshesLoadedPagesBeforeCatchingUpAndPublishesFloorTarget() =
        runTest(testDispatcher) {
            val pager = FakeTopicDetailPager()
            pager.loadFirstResults +=
                Result.success(snapshot(replies = replies(1..100), hasMore = false))
            pager.refreshResults +=
                Result.success(snapshot(replies = replies(1..101), hasMore = false))
            pager.loadUntilFloorResults +=
                Result.success(snapshot(replies = replies(1..101), hasMore = false))
            val viewModel = viewModel(pager)
            advanceUntilIdle()

            viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(101))
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(pager.calls).containsExactly(
                "loadFirst(false)",
                "refreshLoaded()",
                "loadUntilFloor(101)",
            ).inOrder()
            assertThat(state.detail?.replies).hasSize(101)
            assertThat(state.replyFloorTarget).isEqualTo(101)
            viewModel.onEvent(TopicDetailUiEvent.ReplyFloorTargetConsumed)
            assertThat(viewModel.uiState.value.replyFloorTarget).isNull()
        }

    @Test
    fun replyCreated_midFailureFallsBackToLoadedPrefix() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.refreshResults += Result.success(snapshot(replies = replies(1..200), hasMore = true))
        pager.loadUntilFloorResults += Result.failure(networkError())
        // 失败后 ViewModel 会用 loadFirst(false) 同步刷新后的已加载前缀
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..200), hasMore = true))
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(250))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.detail?.replies).hasSize(200)
        assertThat(state.errorMessage).isEqualTo("网络连接失败，请稍后重试")
        assertThat(state.replyFloorTarget).isEqualTo(250)
    }

    @Test
    fun olderReplyFailureFallback_doesNotOverwriteNewerRefresh() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.refreshResults += Result.success(snapshot(replies = replies(1..100), hasMore = true))
        pager.loadUntilFloorResults += Result.failure(networkError())
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        val olderFallback = CompletableDeferred<Result<TopicDetailSnapshot>>()
        pager.deferredLoadFirst += olderFallback
        viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(101))
        runCurrent()

        pager.refreshResults += Result.success(snapshot(replies = replies(1..101), hasMore = false))
        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        runCurrent()
        olderFallback.complete(Result.success(snapshot(replies = replies(1..100), hasMore = true)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.detail?.replies).hasSize(101)
        assertThat(state.errorMessage).isNull()
        assertThat(state.replyFloorTarget).isNull()
    }

    @Test
    fun olderRefreshResult_doesNotOverwriteNewerReplyCatchUp() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        pager.loadFirstResults += Result.success(snapshot(replies = replies(1..10), hasMore = true))
        val olderRefresh = CompletableDeferred<Result<TopicDetailSnapshot>>()
        val replyRefresh = CompletableDeferred<Result<TopicDetailSnapshot>>()
        pager.deferredRefresh += olderRefresh
        pager.deferredRefresh += replyRefresh
        val newerCatchUp = CompletableDeferred<Result<TopicDetailSnapshot>>()
        pager.deferredLoadUntilFloor += newerCatchUp
        val viewModel = viewModel(pager)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.Refresh)
        runCurrent()
        viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(11))
        runCurrent()
        replyRefresh.complete(Result.success(snapshot(replies = replies(1..11), hasMore = false)))
        runCurrent()
        newerCatchUp.complete(Result.success(snapshot(replies = replies(1..11), hasMore = false)))
        runCurrent()
        olderRefresh.complete(Result.success(snapshot(replies = replies(1..10), hasMore = true)))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.detail?.replies).hasSize(11)
        assertThat(state.replyFloorTarget).isEqualTo(11)
    }

    @Test
    fun toggleFavorite_successUpdatesIsFavoritedAndFavoriteOnce() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        val initialDetail = snapshot(replies = replies(1..5), hasMore = false).detail.copy(
            isFavorited = false,
            favoriteOnce = "12345",
        )
        pager.loadFirstResults += Result.success(TopicDetailSnapshot(initialDetail, 1, 1, false))
        val updatedDetail = initialDetail.copy(
            isFavorited = true,
            favoriteOnce = "67890",
        )
        val repository = SinglePagerRepository(pager, setFavoriteResult = Result.success(updatedDetail))
        val viewModel = viewModel(pager, repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.ToggleFavorite)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isTogglingFavorite).isFalse()
        assertThat(state.detail?.isFavorited).isTrue()
        assertThat(state.detail?.favoriteOnce).isEqualTo("67890")
        assertThat(state.favoriteError).isNull()
    }

    @Test
    fun toggleFavorite_failureSetsFavoriteErrorAndClearsOnConsumed() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        val initialDetail = snapshot(replies = replies(1..5), hasMore = false).detail.copy(
            isFavorited = true,
            favoriteOnce = "12345",
        )
        pager.loadFirstResults += Result.success(TopicDetailSnapshot(initialDetail, 1, 1, false))
        val repository = SinglePagerRepository(pager, setFavoriteResult = Result.failure(networkError()))
        val viewModel = viewModel(pager, repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.ToggleFavorite)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isTogglingFavorite).isFalse()
        assertThat(state.detail?.isFavorited).isTrue()
        assertThat(state.favoriteError).isEqualTo("网络连接失败，请稍后重试")

        viewModel.onEvent(TopicDetailUiEvent.FavoriteErrorConsumed)
        assertThat(viewModel.uiState.value.favoriteError).isNull()
    }

    @Test
    fun thankReply_successUpdatesReplyAndRotatesOnce() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        val initial = snapshot(replies = replies(1..2), hasMore = false).detail.copy(thankOnce = "old")
        pager.loadFirstResults += Result.success(TopicDetailSnapshot(initial, 1, 1, false))
        val repository = SinglePagerRepository(
            pager,
            thankReplyResult = Result.success(ThankResult(true, once = "new")),
        )
        val viewModel = viewModel(pager, repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.ThankReply(2))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.detail?.thankOnce).isEqualTo("new")
        assertThat(viewModel.uiState.value.detail?.replies?.single { it.id == 2L }?.isThanked).isTrue()
        assertThat(viewModel.uiState.value.thankingReplyId).isNull()
    }

    @Test
    fun thankReply_stateAndOnceSurviveLoadingNextPage() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        val initial = snapshot(replies = replies(1..2), hasMore = true).detail.copy(thankOnce = "old")
        pager.loadFirstResults += Result.success(TopicDetailSnapshot(initial, 1, 2, true))
        val repository = SinglePagerRepository(
            pager,
            thankReplyResult = Result.success(ThankResult(true, once = "new")),
        )
        val viewModel = viewModel(pager, repository = repository)
        advanceUntilIdle()
        viewModel.onEvent(TopicDetailUiEvent.ThankReply(2))
        advanceUntilIdle()
        val staleNext = snapshot(replies = replies(1..3), hasMore = false).detail.copy(thankOnce = "old")
        pager.loadNextResults += Result.success(TopicDetailSnapshot(staleNext, 2, 2, false))

        viewModel.onEvent(TopicDetailUiEvent.LoadMoreReplies)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.detail?.thankOnce).isEqualTo("new")
        assertThat(viewModel.uiState.value.detail?.replies?.single { it.id == 2L }?.isThanked).isTrue()
    }

    @Test
    fun thankTopic_businessFailureRotatesOnceAndShowsMessage() = runTest(testDispatcher) {
        val pager = FakeTopicDetailPager()
        val initial = snapshot(replies = replies(1..2), hasMore = false).detail.copy(
            isThanked = false,
            thankOnce = "old",
        )
        pager.loadFirstResults += Result.success(TopicDetailSnapshot(initial, 1, 1, false))
        val repository = SinglePagerRepository(
            pager,
            thankTopicResult = Result.success(ThankResult(false, message = "余额不足", once = "new")),
        )
        val viewModel = viewModel(pager, repository = repository)
        advanceUntilIdle()

        viewModel.onEvent(TopicDetailUiEvent.ThankTopic)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.detail?.thankOnce).isEqualTo("new")
        assertThat(viewModel.uiState.value.detail?.isThanked).isFalse()
        assertThat(viewModel.uiState.value.thankError).isEqualTo("余额不足")
    }

    private fun viewModel(
        pager: TopicDetailPager,
        replyFloor: Int? = null,
        repository: SinglePagerRepository = SinglePagerRepository(pager),
    ): TopicDetailViewModel = TopicDetailViewModel(
        savedStateHandle = SavedStateHandle(
            buildMap {
                put("topicId", 1221181L)
                replyFloor?.let { put("replyFloor", it) }
            },
        ),
        getTopicDetailPager = GetTopicDetailUseCase(repository),
        setFavoriteUseCase = app.mystery0.nodeflow.domain.topic.SetFavoriteUseCase(repository),
        thankTopicUseCase = app.mystery0.nodeflow.domain.topic.ThankTopicUseCase(repository),
        thankReplyUseCase = app.mystery0.nodeflow.domain.topic.ThankReplyUseCase(repository),
    )

    private fun accessDenied(): NodeFlowException =
        NodeFlowException(
            kind = NodeFlowException.Kind.AccessDenied,
            message = V2EX_ACCESS_DENIED_MESSAGE,
        )

    private fun networkError(): NodeFlowException =
        NodeFlowException(
            kind = NodeFlowException.Kind.Network,
            message = "网络连接失败，请稍后重试",
        )

    private fun replies(range: IntRange): List<Reply> = range.map { floor ->
        Reply(
            id = floor.toLong(),
            topicId = 1221181,
            floor = floor,
            author = User(username = "user$floor"),
            content = "回复 $floor",
            contentRendered = "<p>回复 $floor</p>",
        )
    }

    private fun snapshot(
        replies: List<Reply>,
        hasMore: Boolean,
    ): TopicDetailSnapshot = TopicDetailSnapshot(
        detail = TopicDetail(
            topic = Topic(
                id = 1221181,
                title = "分页主题",
                url = "https://www.v2ex.com/t/1221181",
                node = Node(name = "python", title = "Python"),
                author = User(username = "alice"),
                replyCount = replies.size,
            ),
            content = "",
            contentRendered = "<p>正文</p>",
            replies = replies,
        ),
        loadedPageCount = (replies.size + 99) / 100,
        totalPageCount = if (hasMore) (replies.size + 99) / 100 + 1 else (replies.size + 99) / 100,
        hasMore = hasMore,
    )

    private class FakeTopicDetailPager : TopicDetailPager {
        val calls = mutableListOf<String>()
        val loadFirstResults = ArrayDeque<Result<TopicDetailSnapshot>>()
        val loadNextResults = ArrayDeque<Result<TopicDetailSnapshot>>()
        val loadUntilFloorResults = ArrayDeque<Result<TopicDetailSnapshot>>()
        val refreshResults = ArrayDeque<Result<TopicDetailSnapshot>>()
        val deferredLoadFirst = ArrayDeque<CompletableDeferred<Result<TopicDetailSnapshot>>>()
        val deferredLoadNext = ArrayDeque<CompletableDeferred<Result<TopicDetailSnapshot>>>()
        val deferredLoadUntilFloor = ArrayDeque<CompletableDeferred<Result<TopicDetailSnapshot>>>()
        val deferredRefresh = ArrayDeque<CompletableDeferred<Result<TopicDetailSnapshot>>>()

        override suspend fun loadFirst(forceRefresh: Boolean): Result<TopicDetailSnapshot> {
            calls += "loadFirst($forceRefresh)"
            deferredLoadFirst.pollFirst()?.let { return it.await() }
            return loadFirstResults.removeFirst()
        }

        override suspend fun loadNext(): Result<TopicDetailSnapshot> {
            calls += "loadNext()"
            deferredLoadNext.pollFirst()?.let { return it.await() }
            return loadNextResults.removeFirst()
        }

        override suspend fun loadUntilFloor(floor: Int): Result<TopicDetailSnapshot> {
            calls += "loadUntilFloor($floor)"
            deferredLoadUntilFloor.pollFirst()?.let { return it.await() }
            return loadUntilFloorResults.removeFirst()
        }

        override suspend fun loadUntilLastPage(): Result<TopicDetailSnapshot> {
            calls += "loadUntilLastPage()"
            error("loadUntilLastPage not expected in these tests")
        }

        override suspend fun refreshLoaded(): Result<TopicDetailSnapshot> {
            calls += "refreshLoaded()"
            deferredRefresh.pollFirst()?.let { return it.await() }
            return refreshResults.removeFirst()
        }
    }

    /** ViewModel 只通过 UseCase 获取 pager，这里用固定实例满足接口。 */
    private class SinglePagerRepository(
        private val pager: TopicDetailPager,
        private val setFavoriteResult: Result<TopicDetail?> = Result.success(null),
        private val thankTopicResult: Result<ThankResult> = Result.failure(UnsupportedOperationException()),
        private val thankReplyResult: Result<ThankResult> = Result.failure(UnsupportedOperationException()),
        private val deferredThankTopic: CompletableDeferred<Result<ThankResult>>? = null,
    ) : TopicRepository {
        override suspend fun latestTopics(forceRefresh: Boolean): Result<List<Topic>> =
            Result.success(emptyList())

        override fun latestTopicsPaging(): Flow<PagingData<Topic>> = flowOf(PagingData.empty())

        override fun topicDetailPager(topicId: Long): TopicDetailPager = pager

        override suspend fun setFavorite(
            topicId: Long,
            favorite: Boolean,
            once: String,
        ): Result<TopicDetail?> = setFavoriteResult

        override suspend fun thankTopic(topicId: Long, once: String): Result<ThankResult> =
            deferredThankTopic?.await() ?: thankTopicResult

        override suspend fun thankReply(
            topicId: Long,
            replyId: Long,
            once: String,
        ): Result<ThankResult> = thankReplyResult

        override suspend fun clearCache() = Unit
    }
}
