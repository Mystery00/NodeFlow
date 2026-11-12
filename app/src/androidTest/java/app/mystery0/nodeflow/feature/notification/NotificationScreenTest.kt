package app.mystery0.nodeflow.feature.notification

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test

class NotificationScreenTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun titleBarHidesOnScrollAndReappearsBeforeReturningToListTop() {
        val notifications = flowOf(PagingData.from(List(80) { index ->
            Notification(
                id = index.toLong(),
                actor = User(username = "reader"),
                action = "回复了主题",
                topicId = index.toLong() + 1,
                topicTitle = "测试通知 $index",
                relativeTime = "刚刚",
            )
        }, LoadStates(
            refresh = LoadState.NotLoading(true),
            prepend = LoadState.NotLoading(true),
            append = LoadState.NotLoading(true),
        )))
        val events = mutableListOf<NotificationUiEvent>()
        rule.setContent {
            NodeFlowTheme(AppSettings()) {
                NotificationScreen(
                    notifications = notifications.collectAsLazyPagingItems(),
                    onEvent = events::add,
                    onUserClick = {},
                    onTopicClick = { _, _ -> },
                )
            }
        }
        rule.onNodeWithText("消息").assertIsDisplayed()
        rule.onNodeWithContentDescription("刷新").assertIsDisplayed()
        rule.onNodeWithText("测试通知 0").assertIsDisplayed()

        val list = rule.onNode(hasScrollAction())
        repeat(2) { list.performTouchInput { swipeUp() } }
        rule.onNodeWithText("消息").assertIsNotDisplayed()
        rule.onNodeWithContentDescription("刷新").assertIsNotDisplayed()

        // 列表仍处于中段时反向滑动，标题栏就应恢复，无需回到第一条。
        list.performTouchInput {
            swipeDown(startY = height * 0.3f, endY = height * 0.5f, durationMillis = 500)
        }
        rule.onNodeWithText("测试通知 0").assertIsNotDisplayed()
        rule.onNodeWithText("消息").assertIsDisplayed()
        rule.onNodeWithContentDescription("刷新").assertIsDisplayed().assertIsEnabled().performClick()
        rule.runOnIdle {
            assertThat(events).containsExactly(NotificationUiEvent.Refresh)
        }
    }
}
