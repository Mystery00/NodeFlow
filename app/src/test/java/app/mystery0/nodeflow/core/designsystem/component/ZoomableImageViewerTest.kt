package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ZoomableImageViewerTest {
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
}
