package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichHtmlTextTest {
    @Test
    fun webViewCssHeightToDp_keepsCssPixelHeightAsDp() {
        assertThat(webViewCssHeightToDp(392).value).isEqualTo(392.dp.value)
    }
}
