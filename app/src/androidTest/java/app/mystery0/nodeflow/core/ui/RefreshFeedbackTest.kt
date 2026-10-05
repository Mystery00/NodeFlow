package app.mystery0.nodeflow.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailScreen
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailUiState
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.model.*
import app.mystery0.nodeflow.feature.home.*
import app.mystery0.nodeflow.feature.node.*
import app.mystery0.nodeflow.feature.notification.NotificationScreen
import app.mystery0.nodeflow.feature.profile.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class RefreshFeedbackTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val error = "刷新失败，请重试"
    private val node = Node(name = "test", title = "测试节点")
    private val topic = Topic(1, "保留的主题", "https://example.invalid/t/1", node, User(username = "tester"))

    @Test fun profileShowsRefreshFailureWithExistingUser() {
        show {
            ProfileScreen(ProfileUiState(username = "tester", isLoading = false,
                user = User(username = "tester"), errorMessage = error), {}, {}, {}, {})
        }
        rule.onNodeWithText(error).assertIsDisplayed()
    }

    @Test fun nodeListShowsRefreshFailureWithExistingNodes() {
        show {
            NodeListScreen(NodeListUiState(isLoading = false, errorMessage = error,
                planes = listOf(NodePlane("test", "测试分组", nodes = listOf(node)))), {}, {})
        }
        rule.onNodeWithText(error).assertIsDisplayed()
    }

    @Test fun homeShowsRefreshFailureWithExistingTopics() {
        show { HomeScreen(HomeUiState(), failedTopics().collectAsLazyPagingItems(), {}, {}, {}) }
        rule.onNodeWithText("保留的主题").assertIsDisplayed()
        rule.onNodeWithText(error).assertIsDisplayed()
    }

    @Test fun nodeShowsRefreshFailureWithExistingTopics() {
        show { NodeScreen(NodeUiState(), failedTopics().collectAsLazyPagingItems(), {}, {}, {}, {}, {}) }
        rule.onNodeWithText("保留的主题").assertIsDisplayed()
        rule.onNodeWithText(error).assertIsDisplayed()
    }

    @Test fun notificationsShowRefreshFailureWithExistingItems() {
        show {
            val flow = remember { flowOf(PagingData.from(listOf(Notification(1, User(username = "tester"),
                "回复", 1, "保留的主题", relativeTime = "刚刚")), failedStates())) }
            NotificationScreen(flow.collectAsLazyPagingItems(), {}, {}, { _, _ -> })
        }
        rule.onNodeWithText("保留的主题").assertIsDisplayed()
        rule.onNodeWithText(error).assertIsDisplayed()
    }

    @Test fun listRefreshExposesProgressSemantics() {
        show { Box { ListRefreshIndicator(true, 1, 0.dp) } }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsDisplayed()
    }

    @Test fun horizontalRefreshExposesProgressSemantics() {
        show { NodeFlowHorizontalRefreshIndicator() }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsDisplayed()
    }

    @Test fun profileRefreshKeepsContentAndStopsProgressAfterCompletion() {
        val state = mutableStateOf(ProfileUiState(username = "tester", isLoading = false,
            user = User(username = "tester"), isRefreshing = true))
        show { ProfileScreen(state.value, {}, {}, {}, {}) }
        rule.onNodeWithText("编辑标签").assertIsDisplayed()
        rule.onNodeWithContentDescription("刷新").assertIsNotEnabled()
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertIsDisplayed()
        rule.runOnIdle { state.value = state.value.copy(isRefreshing = false) }
        rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
    }

    @Test fun topicRefreshAndErrorAreBelowAppBar() {
        val state = mutableStateOf(TopicDetailUiState(isLoading = false, isRefreshing = true,
            detail = TopicDetail(topic, "", "", emptyList())))
        show { TopicDetailScreen(state.value, {}, {}, {}, {}) }
        val appBarBottom = rule.onNodeWithContentDescription("刷新").fetchSemanticsNode().boundsInRoot.bottom
        val progress = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
        progress.assertIsDisplayed()
        assertThat(progress.fetchSemanticsNode().boundsInRoot.top).isAtLeast(appBarBottom)
        rule.runOnIdle { state.value = state.value.copy(isRefreshing = false, errorMessage = error) }
        rule.onNodeWithText(error).assertIsDisplayed()
        assertThat(rule.onNodeWithText(error).fetchSemanticsNode().boundsInRoot.top).isAtLeast(appBarBottom)
    }

    @Test fun profileRefreshErrorRetryDispatchesEvent() {
        val events = mutableListOf<ProfileUiEvent>()
        show { ProfileScreen(ProfileUiState(username = "tester", isLoading = false,
            user = User(username = "tester"), errorMessage = error), events::add, {}, {}, {}) }
        rule.onNodeWithText("重试").performClick()
        rule.runOnIdle { assertThat(events).containsExactly(ProfileUiEvent.Retry) }
    }

    private fun failedStates() = LoadStates(
        LoadState.Error(NodeFlowException(NodeFlowException.Kind.Network, error)),
        LoadState.NotLoading(true), LoadState.NotLoading(true),
    )

    @Composable private fun failedTopics() = remember { flowOf(PagingData.from(listOf(topic), failedStates())) }
    private fun show(content: @Composable () -> Unit) {
        rule.setContent { NodeFlowTheme(AppSettings()) { content() } }
    }
}
