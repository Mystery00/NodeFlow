package app.mystery0.nodeflow.feature.home

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsPagingUseCase
import app.mystery0.nodeflow.domain.topic.TopicRepository
import app.mystery0.nodeflow.testing.FakeNodeRepository
import app.mystery0.nodeflow.testing.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
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
        val pagingNodeNames = mutableListOf<String>()
        val nodeRepository = FakeNodeRepository().apply {
            nodeResult = { name, _ -> Result.success(Node(name = name, title = name)) }
            pagingFactory = { name -> pagingNodeNames += name; flowOf(PagingData.empty()) }
        }
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
        settingsRepository.settingsState.value = AppSettings(
            pinnedHomeNode = PinnedHomeNode(
                name = "android",
                title = "Android",
                avatarUrl = "https://cdn.v2ex.com/navatar/android_large.png",
            ),
        )
        advanceUntilIdle()

        assertThat(topicRepository.latestPagingCalls).isEqualTo(1)
        assertThat(pagingNodeNames).containsExactly("android")
        assertThat(viewModel.uiState.value.title).isEqualTo("Android")

        topicsJob.cancel()
        stateJob.cancel()
    }

    private class FakeTopicRepository : TopicRepository {
        var latestPagingCalls = 0

        override fun latestTopicsPaging(): Flow<PagingData<Topic>> {
            latestPagingCalls += 1
            return flowOf(PagingData.empty())
        }

        override fun topicDetailPager(topicId: Long): app.mystery0.nodeflow.domain.topic.TopicDetailPager =
            error("不应在首页测试中创建主题详情分页器")

        override suspend fun clearCache() = Unit
    }
}
