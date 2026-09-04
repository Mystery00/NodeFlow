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
        assertThat(CONTENT_HEIGHT_SCRIPT).contains("img.complete")
    }

    @Test
    fun parseContentMeasurement_readsHeightAndImageSettledState() {
        assertThat(parseRichHtmlMeasurement("\"392|1\"", fallbackHeightCssPx = 1))
            .isEqualTo(RichHtmlMeasurement(heightCssPx = 392, allImagesSettled = true))
        assertThat(parseRichHtmlMeasurement("\"480|0\"", fallbackHeightCssPx = 1))
            .isEqualTo(RichHtmlMeasurement(heightCssPx = 480, allImagesSettled = false))
    }

    @Test
    fun parseContentMeasurement_invalidResultUsesUnsettledFallback() {
        assertThat(parseRichHtmlMeasurement("null", fallbackHeightCssPx = 120))
            .isEqualTo(RichHtmlMeasurement(heightCssPx = 120, allImagesSettled = false))
    }

    @Test
    fun contentMeasurement_rejectsDetachedAndWrongWidth_thenClassifiesOversizedHeight() {
        assertThat(
            isValidRichHtmlMeasurement(
                heightCssPx = 8_000,
                measuredWidthPx = 1_184,
                expectedWidthPx = 1_184,
                density = 3f,
                isAttachedToWindow = true,
            ),
        ).isTrue()
        assertThat(
            isValidRichHtmlMeasurement(8_000, 1_184, 1_184, 3f, isAttachedToWindow = false),
        ).isFalse()
        assertThat(
            isValidRichHtmlMeasurement(8_000, 1, 1_184, 3f, isAttachedToWindow = true),
        ).isFalse()
        assertThat(isOversizedRichHtmlMeasurement(heightCssPx = 100_000, density = 3f)).isTrue()
        assertThat(isOversizedRichHtmlMeasurement(heightCssPx = 8_000, density = 3f)).isFalse()
    }

    @Test
    fun layoutCache_reusesOnlyMatchingDocumentAndWidth() {
        val cache = RichHtmlLayoutCache()

        cache.put("document-a", widthPx = 1_184, heightDp = 8_000f)

        assertThat(cache.get("document-a", widthPx = 1_184)?.heightDp).isEqualTo(8_000f)
        assertThat(cache.get("document-a", widthPx = 1_184)?.useInternalScroll).isFalse()
        assertThat(cache.get("document-a", widthPx = 1_000)).isNull()
        assertThat(cache.get("document-b", widthPx = 1_184)).isNull()
    }

    @Test
    fun oversizedLayoutCache_restoresFallbackHeightAndInternalScroll() {
        val cache = RichHtmlLayoutCache()

        cache.put(
            document = "document-a",
            widthPx = 1_184,
            heightDp = 800f,
            useInternalScroll = true,
        )

        assertThat(cache.get("document-a", widthPx = 1_184))
            .isEqualTo(RichHtmlCachedLayout(heightDp = 800f, useInternalScroll = true))
        assertThat(oversizedRichHtmlFallbackHeightDp(expectedWidthPx = 1_184, density = 3f))
            .isWithin(0.01f)
            .of(789.33f)
    }

    @Test
    fun layoutCache_keepsFirstStableLayoutUntilExplicitInvalidation() {
        val cache = RichHtmlLayoutCache()

        cache.put("document-a", widthPx = 1_184, heightDp = 800f)
        cache.put("document-a", widthPx = 1_184, heightDp = 900f, useInternalScroll = true)

        assertThat(cache.get("document-a", widthPx = 1_184))
            .isEqualTo(RichHtmlCachedLayout(heightDp = 800f, useInternalScroll = false))

        cache.invalidate("document-a", widthPx = 1_184)

        assertThat(cache.get("document-a", widthPx = 1_184)).isNull()
    }

    @Test
    fun cachedStableHeight_ignoresAllLaterMeasurements() {
        assertThat(
            shouldApplyRichHtmlMeasurement(
                hasCachedStableHeight = false,
            ),
        ).isTrue()
        assertThat(
            shouldApplyRichHtmlMeasurement(
                hasCachedStableHeight = true,
            ),
        ).isFalse()
    }

    @Test
    fun stableHeight_requiresTwoMatchingSettledMeasurements() {
        assertThat(isConfirmedStableRichHtmlHeight(null, heightCssPx = 8_000, allImagesSettled = true)).isFalse()
        assertThat(isConfirmedStableRichHtmlHeight(7_900, heightCssPx = 8_000, allImagesSettled = true)).isFalse()
        assertThat(isConfirmedStableRichHtmlHeight(8_000, heightCssPx = 8_000, allImagesSettled = false)).isFalse()
        assertThat(isConfirmedStableRichHtmlHeight(8_000, heightCssPx = 8_000, allImagesSettled = true)).isTrue()
    }

    @Test
    fun richHtmlWebViewVerticalScroll_isPinnedToTop() {
        assertThat(richHtmlWebViewVerticalScrollY(requestedY = 480)).isEqualTo(0)
        assertThat(richHtmlWebViewVerticalScrollY(requestedY = 480, allowVerticalScroll = true)).isEqualTo(480)
    }

    @Test
    fun internalScroll_releasesGestureToParentOnlyAtMatchingBoundary() {
        assertThat(
            shouldReleaseRichHtmlScrollToParent(
                dragDeltaY = 12f,
                canScrollUp = false,
                canScrollDown = true,
            ),
        ).isTrue()
        assertThat(
            shouldReleaseRichHtmlScrollToParent(
                dragDeltaY = -12f,
                canScrollUp = true,
                canScrollDown = false,
            ),
        ).isTrue()
        assertThat(
            shouldReleaseRichHtmlScrollToParent(
                dragDeltaY = -12f,
                canScrollUp = true,
                canScrollDown = true,
            ),
        ).isFalse()
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
        assertThat(script).contains("layoutInvalidated")
        // 不再按尺寸或相邻文字过滤可点击图片
        assertThat(script).doesNotContain("180")
        assertThat(script).doesNotContain("hasInlineTextSibling")
    }
}
