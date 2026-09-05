package app.mystery0.nodeflow.core.designsystem.component

import android.graphics.drawable.Animatable
import android.util.Base64
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.platform.app.InstrumentationRegistry
import app.mystery0.nodeflow.core.parser.RichContentParser
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File

class RichContentInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun richContent_rendersTableVideoPosterAndIframePlaceholder() {
        val document = RichContentParser.parse(
            """
            <table>
              <caption>参数</caption>
              <tr><th colspan="2">标题</th></tr>
              <tr><td rowspan="2">A</td><td>B</td></tr>
              <tr><td>C</td></tr>
              <tr><td colspan="2"><video><source src="https://media.invalid/nested.mp4"></video></td></tr>
              <tr><td colspan="2"><table><tr><td>内表</td></tr></table></td></tr>
            </table>
            <video><source src="https://example.com/movie.mp4" type="video/mp4"></video>
            <iframe title="外部演示" src="https://example.com/embed/1"></iframe>
            <a href="https://example.com/original"><img class="embedded_image" src="https://example.com/thumb.jpg"></a>
            """.trimIndent(),
        )

        composeRule.setContent {
            MaterialTheme {
                RichContent(document = document, modifier = Modifier.fillMaxWidth())
            }
        }

        composeRule.onNodeWithText("参数").assertIsDisplayed()
        composeRule.onNodeWithText("标题").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("播放视频").assertIsDisplayed()
        composeRule.onNodeWithText("外部演示").assertIsDisplayed()
        composeRule.onNodeWithText("example.com").assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.CollectionInfo,
                CollectionInfo(rowCount = 5, columnCount = 2),
            ),
        ).assertExists()
        composeRule.onNodeWithText("表格内视频请在浏览器中打开").assertExists()
        composeRule.onNodeWithText("暂不支持嵌套表格").assertExists()
        composeRule.onNodeWithContentDescription("打开图片原链接").assertExists()
    }

    @Test
    fun imageLoader_decodesAnimatedGifAsAnimatableDrawable() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gifFile = File(context.cacheDir, "rich-content-animated.gif")
        gifFile.writeBytes(Base64.decode(ANIMATED_GIF_BASE64, Base64.DEFAULT))

        try {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context)
                    .data(gifFile)
                    .build(),
            )

            assertThat(result).isInstanceOf(SuccessResult::class.java)
            assertThat((result as SuccessResult).drawable).isInstanceOf(Animatable::class.java)
        } finally {
            gifFile.delete()
        }
    }

    private companion object {
        const val ANIMATED_GIF_BASE64 =
            "R0lGODlhAgACAIEAAP8AAAAAAAAAAAAAACH/C05FVFNDQVBFMi4wAwEAAAAh+QQACgAAACwAAAAAAgACAAAIBgABCAQQEAAh+QQBCgABACwAAAAAAgACAIEAAP8AAAAAAAAAAAAIBgABCAQQEAA7"
    }
}
