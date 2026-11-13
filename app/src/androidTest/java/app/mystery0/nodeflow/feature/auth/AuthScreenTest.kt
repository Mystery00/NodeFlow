package app.mystery0.nodeflow.feature.auth

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayOutputStream
import org.junit.Rule
import org.junit.Test

class AuthScreenTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun keyboardMovesThroughLoginFieldsAndSubmits() {
        val state = mutableStateOf(readyLogin())
        val events = mutableListOf<AuthUiEvent>()
        rule.setContent { NodeFlowTheme(AppSettings()) { AuthScreen(state.value, events::add, {}, {}) } }
        field("用户名或邮箱").performClick().performImeAction()
        field("密码").assertIsFocused().performImeAction()
        field("图片验证码").assertIsFocused().performImeAction()
        rule.runOnIdle { assertThat(events).containsExactly(AuthUiEvent.Submit) }
    }

    @SdkSuppress(minSdkVersion = 30)
    @Test fun submitRemainsReachableAboveTheKeyboard() {
        rule.setContent { NodeFlowTheme(AppSettings()) { AuthScreen(readyLogin(), {}, {}, {}) } }
        field("密码").performClick()
        rule.waitUntil(5_000) { rule.activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
        rule.onNode(hasText("登录") and hasClickAction()).performScrollTo().assertIsDisplayed()
        rule.runOnIdle {
            assertThat(rule.activity.window.decorView.rootWindowInsets.isVisible(WindowInsets.Type.ime())).isTrue()
        }
    }

    @Test fun passwordPastePreservesTheOriginalValue() {
        val events = mutableListOf<AuthUiEvent>()
        rule.setContent { NodeFlowTheme(AppSettings()) { AuthScreen(readyLogin(), events::add, {}, {}) } }
        field("密码").performTextReplacement(" sample 密码 ")
        rule.runOnIdle { assertThat(events).containsExactly(AuthUiEvent.PasswordChanged(" sample 密码 ")) }
    }

    @Test fun submittingLocksInputsAndCaptchaRefresh() {
        rule.setContent { NodeFlowTheme(AppSettings()) { AuthScreen(readyLogin().copy(isSubmitting = true), {}, {}, {}) } }
        rule.onNodeWithText("用户名或邮箱").assertIsNotEnabled()
        rule.onNodeWithText("密码").assertIsNotEnabled()
        rule.onNode(hasText("图片验证码") and SemanticsMatcher.keyIsDefined(SemanticsProperties.Disabled)).assertIsNotEnabled()
        rule.onNodeWithContentDescription("换一张验证码").assertIsNotEnabled()
        val progress = rule.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNode()
        val density = rule.activity.resources.displayMetrics.density
        assertThat(progress.boundsInRoot.width / density).isAtMost(24f)
        assertThat(progress.boundsInRoot.height / density).isAtMost(24f)
    }

    @Test fun compactLargeTextFormCanReachBothSubmitAndFirstField() {
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                NodeFlowTheme(AppSettings()) {
                    AuthScreen(readyLogin(), {}, {}, {}, modifier = Modifier.width(320.dp).height(420.dp))
                }
            }
        }
        rule.onNode(hasText("登录") and hasClickAction()).performScrollTo().assertIsDisplayed()
        field("用户名或邮箱").performScrollTo().assertIsDisplayed()
    }

    @Test fun twoFactorKeepsAccountContextAndOnlyOffersVerification() {
        val events = mutableListOf<AuthUiEvent>()
        val state = AuthUiState(
            username = "reader@example.test",
            twoFactorChallenge = TwoFactorChallenge("test-only", "服务端较长的提示"),
            twoFactorCode = "123456",
            errorMessage = "验证码无效或已过期",
        )
        rule.setContent { NodeFlowTheme(AppSettings()) { AuthScreen(state, events::add, {}, {}) } }
        rule.onNodeWithText("两步验证").assertIsDisplayed()
        rule.onNodeWithText("reader@example.test", substring = true).assertIsDisplayed()
        field("密码").assertDoesNotExist()
        rule.onNodeWithText("重新获取登录验证码").assertDoesNotExist()
        rule.onNodeWithContentDescription("换一张验证码").assertDoesNotExist()
        field("两步验证码").performImeAction()
        rule.onNodeWithText("切换账号").performClick()
        rule.runOnIdle { assertThat(events).containsExactly(AuthUiEvent.Submit, AuthUiEvent.BackToCredentials).inOrder() }
    }

    @Test fun failedCaptchaCanRetryWithoutSubmittingCredentials() {
        val events = mutableListOf<AuthUiEvent>()
        rule.setContent { NodeFlowTheme(AppSettings()) { AuthScreen(AuthUiState(), events::add, {}, {}) } }
        rule.onNodeWithText("点击重试").assertIsDisplayed()
        rule.onNode(hasText("登录") and hasClickAction()).assertIsNotEnabled()
        rule.onNodeWithContentDescription("换一张验证码").performClick()
        rule.runOnIdle { assertThat(events).containsExactly(AuthUiEvent.RefreshChallenge) }
    }

    private fun field(label: String) = rule.onNode(hasSetTextAction() and hasText(label))
    private fun readyLogin() = AuthUiState(
        challenge = sampleLoginChallenge(), username = "reader@example.test", password = "sample-password", captcha = "A7K2",
    )

    private fun sampleLoginChallenge(): LoginChallenge {
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
}
