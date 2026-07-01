package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RichHtmlTextTest {
    @Test
    fun webViewCssHeightToDp_keepsCssPixelHeightAsDp() {
        assertThat(webViewCssHeightToDp(392).value).isEqualTo(392.dp.value)
    }

    @Test
    fun contentHeightScript_measuresContentWrapperInsteadOfViewport() {
        assertThat(CONTENT_HEIGHT_SCRIPT).contains(".nodeflow-content")
        assertThat(CONTENT_HEIGHT_SCRIPT).doesNotContain("documentElement")
        assertThat(CONTENT_HEIGHT_SCRIPT).doesNotContain("offsetHeight")
    }

    @Test
    fun richHtmlWebViewVerticalScroll_isPinnedToTop() {
        assertThat(richHtmlWebViewVerticalScrollY(requestedY = 480)).isEqualTo(0)
    }

    @Test
    fun richHtmlImageClickScript_filtersInlineImagesAndCallsBridge() {
        val script = richHtmlImageClickScript()

        assertThat(script).contains("NodeFlowImage.open")
        assertThat(script).contains("naturalWidth")
        assertThat(script).contains("innerText")
        assertThat(script).contains("180")
    }
}
