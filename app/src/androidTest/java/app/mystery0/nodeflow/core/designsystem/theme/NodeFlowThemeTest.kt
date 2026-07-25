package app.mystery0.nodeflow.core.designsystem.theme

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.ThemeMode
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NodeFlowThemeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun darkTheme_paintsDarkBackgroundBehindTransparentContent() {
        composeRule.setContent {
            NodeFlowTheme(
                settings = AppSettings(
                    themeMode = ThemeMode.Dark,
                    dynamicColor = false,
                ),
            ) {
                Box(Modifier.fillMaxSize())
            }
        }

        composeRule.waitForIdle()

        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val centerPixel = image[image.width / 2, image.height / 2]

        assertThat(centerPixel.red).isLessThan(0.2f)
        assertThat(centerPixel.green).isLessThan(0.2f)
        assertThat(centerPixel.blue).isLessThan(0.2f)
    }
}
