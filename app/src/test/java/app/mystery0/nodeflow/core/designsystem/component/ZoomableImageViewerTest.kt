package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

class ZoomableImageViewerTest {
    @Test
    fun zoomableImageViewer_delegatesGesturesToZoomImage() {
        val source = zoomableImageViewerSource()

        // 大图手势交给 ZoomImage，避免重新引入自写缩放。
        assertThat(source).contains("import com.github.panpf.zoomimage.CoilZoomAsyncImage")
        assertThat(source).contains("CoilZoomAsyncImage(")
        assertThat(source).doesNotContain("rememberTransformableState")
        assertThat(source).doesNotContain(".transformable(")
        assertThat(source).doesNotContain("graphicsLayer")
        assertThat(source).doesNotContain("detectTapGestures")
    }

    @Test
    fun zoomableImageViewer_supportsImageShare() {
        val source = zoomableImageViewerSource()

        assertThat(source).contains("onShare: (() -> Unit)? = null")
        assertThat(source).contains("Icons.Outlined.Share")
        assertThat(source).contains("isSharing")
        assertThat(source).contains("分享图片")
    }


    @Test
    fun zoomableImageLoadFeedback_showsLoadingBeforeResult() {
        assertThat(
            zoomableImageLoadFeedback(
                isLoading = true,
                isError = false,
            ),
        ).isEqualTo(ZoomableImageLoadFeedback.Loading)
    }

    @Test
    fun zoomableImageLoadFeedback_showsErrorAfterLoadingStops() {
        assertThat(
            zoomableImageLoadFeedback(
                isLoading = false,
                isError = true,
            ),
        ).isEqualTo(ZoomableImageLoadFeedback.Error)
    }

    @Test
    fun zoomableImageLoadFeedback_hidesFeedbackAfterSuccess() {
        assertThat(
            zoomableImageLoadFeedback(
                isLoading = false,
                isError = false,
            ),
        ).isEqualTo(ZoomableImageLoadFeedback.None)
    }

    private fun zoomableImageViewerSource(): String {
        val relativePath =
            "app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/ZoomableImageViewer.kt"
        return generateSequence(File(checkNotNull(System.getProperty("user.dir")))) {
            it.parentFile
        }
            .map { File(it, relativePath) }
            .firstOrNull { it.isFile }
            ?.readText()
            ?: error("找不到 ZoomableImageViewer.kt")
    }
}

