package app.mystery0.nodeflow

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySplashThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun mainActivity_usesStartingTheme() {
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        assertThat(context.resources.getResourceEntryName(activityInfo.theme))
            .isEqualTo("Theme.NodeFlow.Starting")
    }

    @Test
    fun splashBackground_followsSystemNightMode() {
        assertThat(resolveSplashBackground(Configuration.UI_MODE_NIGHT_NO))
            .isEqualTo(0xFFFFFBFE.toInt())
        assertThat(resolveSplashBackground(Configuration.UI_MODE_NIGHT_YES))
            .isEqualTo(0xFF1C1B1F.toInt())
    }

    @Test
    fun splashIcon_usesForegroundAndExplicitBackground() {
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )
        val themedContext = ContextThemeWrapper(context, activityInfo.theme)
        val attributes = themedContext.obtainStyledAttributes(
            intArrayOf(
                androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon,
                androidx.core.splashscreen.R.attr.windowSplashScreenIconBackgroundColor,
            ),
        )

        try {
            assertThat(attributes.getResourceId(0, 0))
                .isEqualTo(R.drawable.ic_launcher_foreground)
            assertThat(attributes.getColor(1, 0))
                .isEqualTo(0xFF202124.toInt())
        } finally {
            attributes.recycle()
        }
    }

    private fun resolveSplashBackground(nightMode: Int): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val resources = context.createConfigurationContext(configuration).resources
        val colorId = resources.getIdentifier(
            "splash_screen_background",
            "color",
            context.packageName,
        )
        assertThat(colorId).isNotEqualTo(0)
        return resources.getColor(colorId, null)
    }
}
