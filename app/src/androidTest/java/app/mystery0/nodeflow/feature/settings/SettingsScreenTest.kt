package app.mystery0.nodeflow.feature.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun contentExceedsAvailableHeight_canScrollToBottom() {
        composeRule.setContent {
            MaterialTheme {
                SettingsScreen(
                    state = SettingsUiState(),
                    onEvent = {},
                    modifier = Modifier.height(360.dp),
                )
            }
        }

        composeRule
            .onNodeWithText("License：Apache License 2.0。")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText("设置").assertIsDisplayed()
    }
}
