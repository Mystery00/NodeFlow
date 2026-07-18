package app.mystery0.nodeflow.feature.home

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.ThemeMode
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.node.NodeRepository
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsPagingUseCase
import app.mystery0.nodeflow.domain.topic.TopicRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class HomeViewModelTest {
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
    fun topics_usesPinnedNodePagingWhenHomeNodeIsPinned() = runTest(testDispatcher) {
        val settingsRepository = FakeSettingsRepository()
        val topicRepository = FakeTopicRepository()
        val nodeRepository = FakeNodeRepository()
        val viewModel = HomeViewModel(
            getLatestTopicsPaging = GetLatestTopicsPagingUseCase(topicRepository),
            getNodeTopicsPaging = GetNodeTopicsPagingUseCase(nodeRepository),
            observeSettings = ObserveSettingsUseCase(settingsRepository),
        )
        val stateJob = launch {
            viewModel.uiState.collect {}
        }
        val topicsJob = launch {
            viewModel.topics.collect {}
        }

        advanceUntilIdle()
        settingsRepository.settingsFlow.value = AppSettings(
            pinnedHomeNode = PinnedHomeNode(
                name = "android",
                title = "Android",
                avatarUrl = "https://cdn.v2ex.com/navatar/android_large.png",
            ),
        )
        advanceUntilIdle()

        assertThat(topicRepository.latestPagingCalls).isEqualTo(1)
        assertThat(nodeRepository.pagingNodeNames).containsExactly("android")
        assertThat(viewModel.uiState.value.title).isEqualTo("Android")

        topicsJob.cancel()
        stateJob.cancel()
    }

    private class FakeSettingsRepository : SettingsRepository {
        val settingsFlow = MutableStateFlow(AppSettings())

        override val settings: Flow<AppSettings> = settingsFlow

        override suspend fun setThemeMode(themeMode: ThemeMode) = Unit

        override suspend fun setDynamicColor(enabled: Boolean) = Unit

        override suspend fun setPinnedHomeNode(node: PinnedHomeNode?) {
            settingsFlow.value = settingsFlow.value.copy(pinnedHomeNode = node)
        }

        override suspend fun setCustomImageHosts(hosts: List<String>) {
            settingsFlow.value = settingsFlow.value.copy(customImageHosts = hosts)
        }

        override suspend fun setShowMemberTags(enabled: Boolean) {
            settingsFlow.value = settingsFlow.value.copy(showMemberTags = enabled)
        }

        override suspend fun clearCache() = Unit
    }

    private class FakeTopicRepository : TopicRepository {
        var latestPagingCalls = 0

        override suspend fun latestTopics(forceRefresh: Boolean): Result<List<Topic>> =
            Result.success(emptyList())

        override fun latestTopicsPaging(): Flow<PagingData<Topic>> {
            latestPagingCalls += 1
            return flowOf(PagingData.empty())
        }

        override fun topicDetailPager(topicId: Long): app.mystery0.nodeflow.domain.topic.TopicDetailPager =
            error("不应在首页测试中创建主题详情分页器")

        override suspend fun clearCache() = Unit
    }

    private class FakeNodeRepository : NodeRepository {
        val pagingNodeNames = mutableListOf<String>()

        override suspend fun nodePlanes(forceRefresh: Boolean): Result<List<NodePlane>> =
            Result.success(emptyList())

        override suspend fun node(name: String, forceRefresh: Boolean): Result<Node> =
            Result.success(Node(name = name, title = name))

        override suspend fun topics(
            name: String,
            page: Int,
            forceRefresh: Boolean,
        ): Result<List<Topic>> = Result.success(emptyList())

        override fun topicsPaging(name: String): Flow<PagingData<Topic>> {
            pagingNodeNames += name
            return flowOf(PagingData.empty())
        }

        override suspend fun clearCache() = Unit
    }
}
