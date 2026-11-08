package app.mystery0.nodeflow.feature.topicdetail

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TopicDetailScreenFeedbackTest {
    @get:Rule val rule = createComposeRule()

    @Test fun favoriteFailureIsVisibleAndCanBeConsumed() {
        val events = mutableListOf<TopicDetailUiEvent>()
        rule.setContent {
            MaterialTheme {
                TopicDetailScreen(TopicDetailUiState(isLoading = false, favoriteError = "当前网络不可用"),
                    events::add, {}, {}, {})
            }
        }
        rule.onNodeWithText("当前网络不可用").assertIsDisplayed()
        rule.onNodeWithText("知道了").performClick()
        assertThat(events).contains(TopicDetailUiEvent.FavoriteErrorConsumed)
    }
}
