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
    fun richHtmlImageScript_managesLoadingErrorAndClickWithoutSizeThreshold() {
        val script = richHtmlImageScript()

        // 加载成功才可点击查看大图，并通过桥接打开
        assertThat(script).contains("NodeFlowImage.open")
        assertThat(script).contains("nf-loaded")
        // 加载/失败占位与失败重试
        assertThat(script).contains("nf-loading")
        assertThat(script).contains("nf-error")
        assertThat(script).contains("nfretry")
        // 不再按尺寸或相邻文字过滤可点击图片
        assertThat(script).doesNotContain("180")
        assertThat(script).doesNotContain("hasInlineTextSibling")
    }
}
