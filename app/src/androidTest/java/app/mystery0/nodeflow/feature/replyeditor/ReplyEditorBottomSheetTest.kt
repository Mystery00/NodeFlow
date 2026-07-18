package app.mystery0.nodeflow.feature.replyeditor

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplyEditorBottomSheetTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Before
    fun setUpEdgeToEdgeWindow() {
        WindowCompat.setDecorFitsSystemWindows(composeRule.activity.window, false)
        composeRule.activity.window.navigationBarColor = AndroidColor.TRANSPARENT
        composeRule.activity.window.isNavigationBarContrastEnforced = false
        WindowInsetsControllerCompat(
            composeRule.activity.window,
            composeRule.activity.window.decorView,
        ).isAppearanceLightNavigationBars = false
    }

    @Test
    fun openSheet_coversBottomNavigationBarAreaWithSheetBackground() {
        composeRule.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surface = Color.Red)) {
                Box(Modifier.fillMaxSize().background(Color.Green)) {
                    ReplyEditorBottomSheet(
                        state = ReplyEditorUiState(isOpen = true),
                        onEvent = {},
                        onPickImage = {},
                        onLoginClick = {},
                    )
                }
            }
        }

        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        val image = composeRule.onRoot().captureToImage().toPixelMap()
        val bottomCenterPixel = image[image.width / 2, image.height - 1]

        assertThat(bottomCenterPixel.green).isLessThan(0.5f)
        assertThat(bottomCenterPixel.red).isGreaterThan(0.5f)
        assertThat(bottomCenterPixel.red).isGreaterThan(bottomCenterPixel.green)
    }

    @Test
    fun openSheet_disablesNavigationBarContrastAndRestoresItAfterClose() {
        var isOpen by mutableStateOf(false)

        composeRule.setContent {
            MaterialTheme {
                ReplyEditorBottomSheet(
                    state = ReplyEditorUiState(isOpen = isOpen),
                    onEvent = {},
                    onPickImage = {},
                    onLoginClick = {},
                )
            }
        }

        composeRule.waitForIdle()
        composeRule.runOnUiThread {
            composeRule.activity.window.isNavigationBarContrastEnforced = true
            isOpen = true
        }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        assertThat(composeRule.activity.window.navigationBarColor).isEqualTo(AndroidColor.TRANSPARENT)
        assertThat(composeRule.activity.window.isNavigationBarContrastEnforced).isFalse()

        composeRule.runOnUiThread { isOpen = false }
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()

        assertThat(composeRule.activity.window.isNavigationBarContrastEnforced).isTrue()
    }
}
