package app.mystery0.nodeflow.feature.auth

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.ThemeMode
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** 显式启用的原生设计截图工具；只使用虚构表单，不创建 ViewModel 或访问网络。 */
@SdkSuppress(minSdkVersion = 30)
class AuthDesignCaptureTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun captureAuthStates() {
        val phase = InstrumentationRegistry.getArguments().getString("authDesignCapture")
        assumeTrue(phase == "before" || phase == "after")
        val state = mutableStateOf(AuthUiState(challenge = sampleLoginChallenge()))
        val theme = mutableStateOf(ThemeMode.Light)
        val fontScale = mutableStateOf(1f)
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                NodeFlowTheme(AppSettings(themeMode = theme.value)) {
                    AuthScreen(state.value, {}, {}, {})
                }
            }
        }
        capture(phase!!, "01-login")
        rule.runOnIdle { state.value = state.value.copy(username = "reader@example.test", password = "sample-password") }
        rule.onNodeWithText("密码").performClick()
        rule.waitUntil(5_000) { rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        capture(phase, "02-login-keyboard", hideKeyboard = false)
        if (phase == "after") {
            rule.onNode(hasText("登录") and hasClickAction()).performScrollTo()
            capture(phase, "07-login-keyboard-submit", hideKeyboard = false)
        }
        rule.runOnIdle {
            state.value = AuthUiState(
                username = "reader@example.test",
                twoFactorChallenge = TwoFactorChallenge("test-only", "你的 V2EX 账号已经开启了两步验证，请输入验证码继续"),
            )
        }
        capture(phase, "03-two-factor")
        rule.runOnIdle {
            state.value = state.value.copy(twoFactorCode = "123456", errorMessage = "验证码无效或已过期，请输入当前验证码。")
        }
        capture(phase, "04-two-factor-error")
        rule.runOnIdle {
            state.value = state.value.copy(isSubmitting = true, errorMessage = null)
        }
        capture(phase, "05-two-factor-submitting")
        rule.runOnIdle {
            theme.value = ThemeMode.Dark
            state.value = AuthUiState(challenge = sampleLoginChallenge())
        }
        capture(phase, "06-login-dark")
        if (phase == "after") {
            rule.runOnIdle {
                theme.value = ThemeMode.Light
                state.value = AuthUiState(errorMessage = "网络暂时不可用，请稍后重试")
            }
            capture(phase, "08-captcha-failed")
            rule.runOnIdle {
                fontScale.value = 2f
                state.value = AuthUiState(
                    username = "very-long-reader-name@example.test",
                    twoFactorChallenge = TwoFactorChallenge("test-only", "两步验证"),
                    twoFactorCode = "123456",
                )
            }
            rule.onNode(hasText("验证并登录") and hasClickAction()).performScrollTo()
            capture(phase, "09-two-factor-large-font")
        }
    }

    private fun capture(phase: String, name: String, hideKeyboard: Boolean = true) {
        rule.waitForIdle()
        if (hideKeyboard) {
            rule.runOnIdle { rule.activity.window.decorView.windowInsetsController?.hide(WindowInsets.Type.ime()) }
            rule.waitUntil(5_000) { rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) != true }
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(300, 3_000)
        val image = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "auth-design/$phase").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
        image.recycle()
    }
}

internal fun sampleLoginChallenge(): LoginChallenge {
    val bitmap = Bitmap.createBitmap(280, 80, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    canvas.drawText("A7K2", 64f, 58f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 52f })
    val bytes = ByteArrayOutputStream().use { stream ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.toByteArray()
    }
    bitmap.recycle()
    return LoginChallenge("username", "password", "captcha", "test-only", "/mission/daily", "/_captcha", bytes)
}
