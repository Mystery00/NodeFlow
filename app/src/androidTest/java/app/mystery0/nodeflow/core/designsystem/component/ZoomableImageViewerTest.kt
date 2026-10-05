package app.mystery0.nodeflow.core.designsystem.component

import android.graphics.Bitmap
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Rule
import org.junit.Test

class ZoomableImageViewerTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun localImageLoadsAndShareAndCloseCallbacksWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File.createTempFile("zoom-preview-", ".png", context.cacheDir)
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            // 本地生成图片，避免依赖真实图床或账号数据。
            bitmap.eraseColor(Color.BLUE)
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            var shared = false
            var closed = false
            rule.setContent {
                MaterialTheme {
                    ZoomableImageViewer(image.toURI().toString(), { closed = true }, onShare = { shared = true })
                }
            }
            rule.waitUntil(timeoutMillis = 10_000) {
                rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
                    .fetchSemanticsNodes().isEmpty()
            }
            rule.onNodeWithText("图片加载失败").assertDoesNotExist()
            rule.onNodeWithContentDescription("分享图片").assertIsDisplayed().performClick()
            rule.onNodeWithContentDescription("关闭大图").assertIsDisplayed().performClick()
            rule.runOnIdle {
                assertThat(shared).isTrue()
                assertThat(closed).isTrue()
            }
        } finally {
            bitmap.recycle()
            image.delete()
        }
    }

    @Test fun missingImageShowsFailureAndDisablesShare() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val missing = File(context.cacheDir, "missing-${System.nanoTime()}.png")
        rule.setContent {
            MaterialTheme { ZoomableImageViewer(missing.toURI().toString(), {}, onShare = {}) }
        }
        rule.waitUntil(timeoutMillis = 10_000) {
            rule.onAllNodesWithText("图片加载失败").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("图片加载失败").assertIsDisplayed()
        rule.onNodeWithContentDescription("分享图片").assertIsNotEnabled()
        rule.onNodeWithContentDescription("关闭大图").assertIsDisplayed()
    }
}
