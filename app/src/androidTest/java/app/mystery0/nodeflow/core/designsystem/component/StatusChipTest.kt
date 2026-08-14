package app.mystery0.nodeflow.core.designsystem.component

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.model.AppSettings
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StatusChipTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun statusChip_isDisplayedWithoutClickAction() {
        composeRule.setContent {
            NodeFlowTheme(settings = AppSettings(dynamicColor = false)) {
                StatusChip(title = "置顶")
            }
        }

        composeRule.onNodeWithText("置顶")
            .assertIsDisplayed()
            .assert(!hasClickAction())
    }

    @Test
    fun nodeChip_remainsClickableAfterSharingVisualBase() {
        var clicked = false
        composeRule.setContent {
            NodeFlowTheme(settings = AppSettings(dynamicColor = false)) {
                NodeChip(
                    title = "Android",
                    onClick = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("Android")
            .assert(hasClickAction())
            .performClick()
        composeRule.runOnIdle {
            assertThat(clicked).isTrue()
        }
    }
}
