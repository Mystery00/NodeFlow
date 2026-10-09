package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.model.RichInline
import app.mystery0.nodeflow.core.parser.RichContentParser
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import java.util.Base64

class Base64RevealInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun shortInline_eyeReplacesTextAndSurvivesRecreation() {
        val encoded = "aGVsbG8gd29ybGQ="
        val document = RichContentParser.parse("<p>前 $encoded 后</p>", contentKey = "topic:1")
        val visible = mutableStateOf(true)
        val state = Base64RevealState()
        var navigations = 0
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalBase64RevealState provides state) {
                    if (visible.value) RichContent(document, onUrlClick = { navigations++; true })
                }
            }
        }
        composeRule.onNodeWithText("hello world", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("显示解码内容").performClick()
        composeRule.onNodeWithText("hello world", useUnmergedTree = true).assertIsDisplayed()
        composeRule.runOnIdle { assertThat(navigations).isEqualTo(0); visible.value = false }
        composeRule.waitForIdle()
        composeRule.runOnIdle { visible.value = true }
        composeRule.onNodeWithText("hello world", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("隐藏解码内容").performClick()
        composeRule.onNodeWithText(encoded, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("hello world", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test fun decodedLink_opensOnlyWhenLinkIsClicked() {
        val url = "https://example.com/path"
        val token = RichInline.Base64Text(encode(url), "reply:1:0")
        val opened = mutableListOf<String>()
        val state = Base64RevealState()
        composeRule.setContent {
            MaterialTheme {
                Base64RevealContent(token, state, { opened += it }, Modifier.width(300.dp))
            }
        }
        composeRule.onNodeWithContentDescription("显示解码内容").performClick()
        composeRule.runOnIdle { assertThat(opened).isEmpty() }
        composeRule.onNodeWithText(url, useUnmergedTree = true).performTouchInput { click(center) }
        composeRule.runOnIdle { assertThat(opened).containsExactly(url) }
    }

    @Test fun reply_longBlockWrapsAndRestoresOriginalWithFormatting() {
        val decoded = "这是一段需要换行的文字。\n第二行仍在同一小块中。"
        val encoded = encode(decoded)
        composeRule.setContent {
            MaterialTheme {
                HtmlText("<blockquote><b>引用</b></blockquote><p>前 $encoded 后</p>",
                    contentKey = "reply:1:2", modifier = Modifier.width(200.dp))
            }
        }
        composeRule.onNodeWithText("引用").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("显示解码内容").performClick()
        composeRule.onNodeWithText(decoded, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithContentDescription("隐藏解码内容").performClick()
        composeRule.onNodeWithText(encoded, useUnmergedTree = true).assertIsDisplayed()
    }

    private fun encode(text: String) = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
}
