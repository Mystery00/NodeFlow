package app.mystery0.nodeflow.core.crash

import android.app.Application
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CrashLayoutTest {
    @get:Rule val rule = createComposeRule()

    @Test fun longStackFrameWrapsWithinScreenWidth() {
        val report = "at app.mystery0.nodeflow." + "LongClassName".repeat(30) + ".invoke(Test.kt:42)"
        val context = RuntimeEnvironment.getApplication()
        val activity = Robolectric.buildActivity(CrashActivity::class.java, CrashActivity.newIntent(context, report)).setup()
        try {
            val layouts = mutableListOf<TextLayoutResult>()
            rule.onNodeWithText(report).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertThat(layouts.single().lineCount).isGreaterThan(1)
        } finally { activity.pause().stop().destroy() }
    }
}
