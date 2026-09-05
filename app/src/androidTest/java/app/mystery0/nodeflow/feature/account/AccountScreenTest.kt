package app.mystery0.nodeflow.feature.account

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.mystery0.nodeflow.core.designsystem.theme.NodeFlowTheme
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.AccountWealth
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.DailyCheckIn
import app.mystery0.nodeflow.core.model.ThemeMode
import app.mystery0.nodeflow.core.model.User
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class AccountScreenTest {
    @get:Rule val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun logoutRequiresConfirmationAndMenuCallbacksWork() {
        val events = mutableListOf<AccountUiEvent>()
        var settingsOpened = false
        var favoritesOpened = false
        composeRule.setContent {
            NodeFlowTheme(AppSettings(themeMode = ThemeMode.Light)) {
                AccountScreen(sampleState(), events::add, { settingsOpened = true }, {}, { favoritesOpened = true })
            }
        }
        savePreview("account-light.png")
        composeRule.onNodeWithText("收藏主题").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("设置").assertIsDisplayed().performClick()
        composeRule.runOnIdle {
            assertThat(settingsOpened).isTrue()
            assertThat(favoritesOpened).isTrue()
        }
        composeRule.onNodeWithText("退出登录").performScrollTo().performClick()
        composeRule.runOnIdle { assertThat(events).doesNotContain(AccountUiEvent.Logout) }
        composeRule.onNodeWithText("取消").performClick()
        composeRule.onNodeWithText("确认退出").assertDoesNotExist()
        composeRule.runOnIdle { assertThat(events).doesNotContain(AccountUiEvent.Logout) }
        composeRule.onNodeWithText("退出登录").performClick()
        composeRule.onNodeWithText("确认退出").performClick()
        composeRule.runOnIdle { assertThat(events.count { it == AccountUiEvent.Logout }).isEqualTo(1) }
    }

    @Test
    fun darkThemeShowsCheckedInStateWithoutActionButton() {
        composeRule.setContent {
            NodeFlowTheme(AppSettings(themeMode = ThemeMode.Dark)) {
                AccountScreen(sampleState(checkedIn = true), {}, {}, {}, {})
            }
        }
        composeRule.onNodeWithText("今日已签到").assertIsDisplayed()
        composeRule.onNodeWithText("签到").assertDoesNotExist()
        savePreview("account-dark.png")
    }

    @Test
    fun largeFontAndNarrowScreenWrapCheckInAndKeepLogoutReachable() {
        composeRule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale = 2f)) {
                NodeFlowTheme(AppSettings(themeMode = ThemeMode.Light)) {
                    AccountScreen(sampleState(), {}, {}, {}, {}, modifier = Modifier.requiredSize(320.dp, 600.dp))
                }
            }
        }
        composeRule.onNodeWithText("签到").performScrollTo().assertIsDisplayed()
        val status = composeRule.onNodeWithText("已连续签到 128 天").fetchSemanticsNode().boundsInRoot
        val action = composeRule.onNodeWithText("签到").fetchSemanticsNode().boundsInRoot
        assertThat(action.top).isAtLeast(status.bottom)
        composeRule.onNodeWithText("退出登录").performScrollTo().assertIsDisplayed().performClick()
        composeRule.onNodeWithText("取消").assertIsDisplayed().performClick()
    }

    @Test
    fun signedOutKeepsSettingsAvailable() {
        var opened = false
        composeRule.setContent {
            NodeFlowTheme(AppSettings()) { AccountScreen(AccountUiState(), {}, { opened = true }, {}, {}) }
        }
        composeRule.onNodeWithText("登录后查看收藏主题、回复帖子和接收消息。").assertIsDisplayed()
        composeRule.onNodeWithText("设置").performClick()
        composeRule.runOnIdle { assertThat(opened).isTrue() }
        composeRule.onNodeWithText("退出登录").assertDoesNotExist()
    }

    private fun savePreview(name: String) {
        // 预览只使用下方虚构数据，不访问真实账号，也不包含真实用户头像或内容。
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        File(context.cacheDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun sampleState(checkedIn: Boolean = false) = AccountUiState(
        session = AuthSession(username = "NodeFlow", cookieHeader = "test-session"),
        user = User(
            username = "NodeFlow",
            tagline = "探索技术，分享生活。",
            memberNumber = 12345,
            createdAtEpochSeconds = Instant.parse("2020-06-01T00:00:00Z").epochSecond,
            dailyActivityRank = 42,
            bio = "保持好奇，记录每一次发现。",
        ),
        overview = AccountOverview(
            wealth = AccountWealth(gold = 2, silver = 36, bronze = 128),
            checkIn = DailyCheckIn(checkedIn = checkedIn, continuousDays = 128, canCheckIn = !checkedIn),
        ),
    )
}
