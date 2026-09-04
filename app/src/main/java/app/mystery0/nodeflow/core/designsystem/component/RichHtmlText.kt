package app.mystery0.nodeflow.core.designsystem.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

class RichHtmlLayoutCache {
    private val stableLayouts = mutableMapOf<RichHtmlLayoutCacheKey, RichHtmlCachedLayout>()

    internal fun get(
        document: String,
        widthPx: Int,
    ): RichHtmlCachedLayout? = stableLayouts[RichHtmlLayoutCacheKey(document, widthPx)]

    internal fun put(
        document: String,
        widthPx: Int,
        heightDp: Float,
        useInternalScroll: Boolean = false,
    ) {
        val key = RichHtmlLayoutCacheKey(document, widthPx)
        if (key !in stableLayouts) {
            stableLayouts[key] = RichHtmlCachedLayout(
                heightDp = heightDp,
                useInternalScroll = useInternalScroll,
            )
        }
    }

    internal fun invalidate(document: String, widthPx: Int) {
        stableLayouts.remove(RichHtmlLayoutCacheKey(document, widthPx))
    }
}

internal data class RichHtmlCachedLayout(
    val heightDp: Float,
    val useInternalScroll: Boolean,
)

private data class RichHtmlLayoutCacheKey(
    val document: String,
    val widthPx: Int,
)

@Composable
fun rememberRichHtmlLayoutCache(key: Any? = Unit): RichHtmlLayoutCache =
    remember(key) { RichHtmlLayoutCache() }

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichHtmlText(
    html: String,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.Transparent,
    onImageClick: (String) -> Unit = {},
    onUrlClick: (String) -> Boolean = { false },
    layoutCache: RichHtmlLayoutCache = rememberRichHtmlLayoutCache(html),
) {
    if (html.isBlank()) return
    val context = LocalContext.current
    val currentOnImageClick = rememberUpdatedState(onImageClick)
    val currentOnUrlClick = rememberUpdatedState(onUrlClick)
    val colorScheme = MaterialTheme.colorScheme
    val customImageHosts = LocalCustomImageHosts.current
    val cssBackgroundColor = if (backgroundColor == Color.Transparent || backgroundColor == Color.Unspecified) {
        "transparent"
    } else {
        backgroundColor.toCssColor()
    }
    val htmlDocument = remember(html, colorScheme, customImageHosts, cssBackgroundColor) {
        buildV2exHtmlDocument(
            bodyHtml = html,
            colors = V2exHtmlColors(
                text = colorScheme.onSurface.toCssColor(),
                secondaryText = colorScheme.onSurfaceVariant.toCssColor(),
                link = colorScheme.primary.toCssColor(),
                background = cssBackgroundColor,
                codeBackground = colorScheme.surfaceVariant.copy(alpha = 0.42f).toCssColor(),
                quoteBackground = colorScheme.surfaceVariant.copy(alpha = 0.34f).toCssColor(),
                border = colorScheme.outlineVariant.copy(alpha = 0.72f).toCssColor(),
            ),
            customImageHosts = customImageHosts,
        )
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val expectedWidthPx = with(density) { maxWidth.roundToPx() }
        val cachedLayout = layoutCache.get(htmlDocument, expectedWidthPx)
        var contentHeight by remember(htmlDocument, expectedWidthPx, layoutCache) {
            mutableStateOf((cachedLayout?.heightDp ?: 1f).dp)
        }
        var hasCachedStableHeight by remember(htmlDocument, expectedWidthPx, layoutCache) {
            mutableStateOf(cachedLayout != null)
        }
        var useInternalScroll by remember(htmlDocument, expectedWidthPx, layoutCache) {
            mutableStateOf(cachedLayout?.useInternalScroll == true)
        }
        var stableHeightCandidate by remember(htmlDocument, expectedWidthPx, layoutCache) {
            mutableStateOf<Int?>(null)
        }
        var stableConfirmationAttempts by remember(htmlDocument, expectedWidthPx, layoutCache) {
            mutableIntStateOf(0)
        }

        fun updateContentHeight(view: WebView) {
            if (!view.isAttachedToWindow || view.width <= 0) return
            view.evaluateJavascript(CONTENT_HEIGHT_SCRIPT) { value ->
                val measurement = parseRichHtmlMeasurement(value, view.contentHeight)
                if (
                    !isValidRichHtmlMeasurement(
                        heightCssPx = measurement.heightCssPx,
                        measuredWidthPx = view.width,
                        expectedWidthPx = expectedWidthPx,
                        density = density.density,
                        isAttachedToWindow = view.isAttachedToWindow,
                    )
                ) {
                    return@evaluateJavascript
                }
                val stableHeightConfirmed = isConfirmedStableRichHtmlHeight(
                    previousHeightCssPx = stableHeightCandidate,
                    heightCssPx = measurement.heightCssPx,
                    allImagesSettled = measurement.allImagesSettled,
                )
                val oversized = isOversizedRichHtmlMeasurement(
                    heightCssPx = measurement.heightCssPx,
                    density = density.density,
                )
                if (
                    !hasCachedStableHeight &&
                    measurement.allImagesSettled &&
                    !stableHeightConfirmed &&
                    stableConfirmationAttempts < MAX_STABLE_HEIGHT_CONFIRM_ATTEMPTS
                ) {
                    stableConfirmationAttempts += 1
                    view.postDelayed(
                        { updateContentHeight(view) },
                        STABLE_HEIGHT_CONFIRM_DELAY_MILLIS,
                    )
                }
                if (oversized) {
                    stableHeightCandidate = measurement.heightCssPx.takeIf { measurement.allImagesSettled }
                    if (stableHeightConfirmed && !hasCachedStableHeight) {
                        val fallbackHeightDp = oversizedRichHtmlFallbackHeightDp(
                            expectedWidthPx = expectedWidthPx,
                            density = density.density,
                        )
                        useInternalScroll = true
                        (view as? RichHtmlWebView)?.allowVerticalScroll = true
                        contentHeight = fallbackHeightDp.dp
                        layoutCache.put(
                            document = htmlDocument,
                            widthPx = expectedWidthPx,
                            heightDp = fallbackHeightDp,
                            useInternalScroll = true,
                        )
                        hasCachedStableHeight = true
                    }
                    return@evaluateJavascript
                }
                if (
                    shouldApplyRichHtmlMeasurement(
                        hasCachedStableHeight = hasCachedStableHeight,
                    )
                ) {
                    useInternalScroll = false
                    (view as? RichHtmlWebView)?.allowVerticalScroll = false
                    val nextHeight = webViewCssHeightToDp(measurement.heightCssPx)
                    if (kotlin.math.abs(nextHeight.value - contentHeight.value) > 1f) {
                        contentHeight = nextHeight
                    }
                }
                stableHeightCandidate = measurement.heightCssPx.takeIf { measurement.allImagesSettled }
                if (stableHeightConfirmed && !hasCachedStableHeight) {
                    layoutCache.put(htmlDocument, expectedWidthPx, measurement.heightCssPx.toFloat())
                    hasCachedStableHeight = true
                }
            }
        }

        fun scheduleHeightUpdates(view: WebView) {
            listOf(0L, 80L, 240L, 600L, 1200L).forEach { delayMillis ->
                view.postDelayed({ updateContentHeight(view) }, delayMillis)
            }
        }

        fun installImageManager(view: WebView) {
            view.evaluateJavascript(richHtmlImageScript(), null)
        }

        key(htmlDocument, expectedWidthPx) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(contentHeight),
                factory = {
                    val webView = RichHtmlWebView(context)
                    val callbacks = webView.callbacks
                    webView.allowVerticalScroll = useInternalScroll
                    webView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    webView.isVerticalScrollBarEnabled = false
                    webView.isHorizontalScrollBarEnabled = false
                    webView.overScrollMode = WebView.OVER_SCROLL_NEVER
                    webView.settings.javaScriptEnabled = true
                    webView.settings.defaultTextEncodingName = "utf-8"
                    webView.settings.loadWithOverviewMode = false
                    webView.settings.useWideViewPort = false
                    webView.settings.builtInZoomControls = false
                    webView.settings.displayZoomControls = false
                    webView.addJavascriptInterface(
                        RichHtmlImageBridge(
                            onImageClick = callbacks::openImage,
                            onContentChanged = callbacks::contentChanged,
                            onLayoutInvalidated = callbacks::layoutInvalidated,
                        ),
                        NODEFLOW_IMAGE_BRIDGE,
                    )
                    webView.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                            callbacks.openUrl(request.url.toString()) || context.openExternalUri(request.url)

                        override fun onPageFinished(view: WebView, url: String?) {
                            callbacks.documentLoaded()
                        }
                    }
                    webView.webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            if (newProgress == 100) {
                                callbacks.documentLoaded()
                            }
                        }
                    }
                    webView
                },
                update = { view ->
                    view.callbacks.bind(
                        RichHtmlWebViewCallbackSet(
                            onImageClick = { url -> view.post { currentOnImageClick.value(url) } },
                            onContentChanged = { view.post { updateContentHeight(view) } },
                            onLayoutInvalidated = {
                                view.post {
                                    layoutCache.invalidate(htmlDocument, expectedWidthPx)
                                    hasCachedStableHeight = false
                                    stableHeightCandidate = null
                                    stableConfirmationAttempts = 0
                                }
                            },
                            onUrlClick = { url -> currentOnUrlClick.value(url) },
                            onDocumentLoaded = {
                                installImageManager(view)
                                scheduleHeightUpdates(view)
                            },
                            onReattached = { scheduleHeightUpdates(view) },
                        ),
                    )
                    view.allowVerticalScroll = useInternalScroll
                    if (shouldLoadRichHtmlDocument(view.tag, htmlDocument)) {
                        view.tag = htmlDocument
                        view.loadDataWithBaseURL(
                            "https://www.v2ex.com/",
                            htmlDocument,
                            "text/html",
                            "utf-8",
                            null,
                        )
                        installImageManager(view)
                        scheduleHeightUpdates(view)
                    }
                    view.resumeAfterReuse()
                },
                onReset = { view ->
                    view.prepareForReuse()
                },
                onRelease = { view ->
                    view.release()
                },
            )
        }
    }
}

internal fun shouldLoadRichHtmlDocument(
    currentDocument: Any?,
    nextDocument: String,
): Boolean = currentDocument != nextDocument

private class RichHtmlWebView(context: Context) : WebView(context) {
    val callbacks = RichHtmlWebViewCallbacks()
    private val reuseState = RichHtmlWebViewReuseState()
    var allowVerticalScroll: Boolean = false
    private var lastTouchY: Float = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!allowVerticalScroll) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_MOVE -> {
                val dragDeltaY = event.y - lastTouchY
                val releaseToParent = shouldReleaseRichHtmlScrollToParent(
                    dragDeltaY = dragDeltaY,
                    canScrollUp = canScrollVertically(-1),
                    canScrollDown = canScrollVertically(1),
                )
                parent?.requestDisallowInterceptTouchEvent(!releaseToParent)
                lastTouchY = event.y
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL,
            -> parent?.requestDisallowInterceptTouchEvent(false)
        }
        return super.onTouchEvent(event)
    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, richHtmlWebViewVerticalScrollY(y, allowVerticalScroll))
    }

    override fun onOverScrolled(
        scrollX: Int,
        scrollY: Int,
        clampedX: Boolean,
        clampedY: Boolean,
    ) {
        super.onOverScrolled(
            scrollX,
            richHtmlWebViewVerticalScrollY(scrollY, allowVerticalScroll),
            clampedX,
            if (allowVerticalScroll) clampedY else true,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resumeAfterReuse()
        post {
            scrollTo(scrollX, 0)
            invalidate()
        }
    }

    fun prepareForReuse() {
        callbacks.clear()
        reuseState.markReset()
    }

    fun resumeAfterReuse() {
        if (
            reuseState.consumeMeasurementRequest(
                callbacksBound = callbacks.isBound,
                isAttachedToWindow = isAttachedToWindow,
            )
        ) {
            post { callbacks.reattached() }
        }
    }

    fun release() {
        callbacks.clear()
        stopLoading()
        removeJavascriptInterface(NODEFLOW_IMAGE_BRIDGE)
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        destroy()
    }
}

internal data class RichHtmlWebViewCallbackSet(
    val onImageClick: (String) -> Unit = {},
    val onContentChanged: () -> Unit = {},
    val onLayoutInvalidated: () -> Unit = {},
    val onUrlClick: (String) -> Boolean = { false },
    val onDocumentLoaded: () -> Unit = {},
    val onReattached: () -> Unit = {},
)

internal class RichHtmlWebViewCallbacks {
    @Volatile
    private var callbackSet: RichHtmlWebViewCallbackSet? = null

    val isBound: Boolean
        get() = callbackSet != null

    fun bind(callbackSet: RichHtmlWebViewCallbackSet) {
        this.callbackSet = callbackSet
    }

    fun clear() {
        callbackSet = null
    }

    fun openImage(url: String) {
        callbackSet?.onImageClick?.invoke(url)
    }

    fun contentChanged() {
        callbackSet?.onContentChanged?.invoke()
    }

    fun layoutInvalidated() {
        callbackSet?.onLayoutInvalidated?.invoke()
    }

    fun openUrl(url: String): Boolean = callbackSet?.onUrlClick?.invoke(url) == true

    fun documentLoaded() {
        callbackSet?.onDocumentLoaded?.invoke()
    }

    fun reattached() {
        callbackSet?.onReattached?.invoke()
    }
}

internal class RichHtmlWebViewReuseState {
    private var needsMeasurementAfterReset = false

    fun markReset() {
        needsMeasurementAfterReset = true
    }

    fun consumeMeasurementRequest(
        callbacksBound: Boolean,
        isAttachedToWindow: Boolean,
    ): Boolean {
        if (!needsMeasurementAfterReset || !callbacksBound || !isAttachedToWindow) return false
        needsMeasurementAfterReset = false
        return true
    }
}

private class RichHtmlImageBridge(
    private val onImageClick: (String) -> Unit,
    private val onContentChanged: () -> Unit,
    private val onLayoutInvalidated: () -> Unit,
) {
    @JavascriptInterface
    fun open(url: String) {
        onImageClick(url)
    }

    @JavascriptInterface
    fun contentChanged() {
        onContentChanged()
    }

    @JavascriptInterface
    fun layoutInvalidated() {
        onLayoutInvalidated()
    }
}

internal fun richHtmlWebViewVerticalScrollY(
    requestedY: Int,
    allowVerticalScroll: Boolean = false,
): Int = if (allowVerticalScroll) requestedY.coerceAtLeast(0) else 0

internal fun shouldReleaseRichHtmlScrollToParent(
    dragDeltaY: Float,
    canScrollUp: Boolean,
    canScrollDown: Boolean,
): Boolean =
    (dragDeltaY > 0f && !canScrollUp) ||
        (dragDeltaY < 0f && !canScrollDown)

private fun Color.toCssColor(): String {
    val argb = toArgb()
    val alpha = android.graphics.Color.alpha(argb) / 255f
    return "rgba(${android.graphics.Color.red(argb)}, ${android.graphics.Color.green(argb)}, ${android.graphics.Color.blue(argb)}, $alpha)"
}

internal fun webViewCssHeightToDp(cssPixels: Int): Dp =
    cssPixels.coerceAtLeast(1).dp

internal data class RichHtmlMeasurement(
    val heightCssPx: Int,
    val allImagesSettled: Boolean,
)

internal fun parseRichHtmlMeasurement(
    value: String?,
    fallbackHeightCssPx: Int,
): RichHtmlMeasurement {
    val parts = value?.trim('"')?.split('|', limit = 2)
    val height = parts
        ?.getOrNull(0)
        ?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it in 1.0..Int.MAX_VALUE.toDouble() }
        ?.roundToInt()
    val allImagesSettled = height != null && parts.getOrNull(1) == "1"
    return RichHtmlMeasurement(
        heightCssPx = height ?: fallbackHeightCssPx,
        allImagesSettled = allImagesSettled,
    )
}

internal fun isValidRichHtmlMeasurement(
    heightCssPx: Int,
    measuredWidthPx: Int,
    expectedWidthPx: Int,
    density: Float,
    isAttachedToWindow: Boolean,
): Boolean {
    if (!isAttachedToWindow || heightCssPx <= 0 || measuredWidthPx <= 0 || expectedWidthPx <= 0) return false
    if (kotlin.math.abs(measuredWidthPx - expectedWidthPx) > 1) return false
    return density.isFinite() && density > 0f
}

internal fun isOversizedRichHtmlMeasurement(
    heightCssPx: Int,
    density: Float,
): Boolean {
    val heightPx = heightCssPx.toDouble() * density.toDouble()
    return !heightPx.isFinite() || heightPx > MAX_COMPOSE_CONSTRAINT_SIZE_PX
}

internal fun oversizedRichHtmlFallbackHeightDp(
    expectedWidthPx: Int,
    density: Float,
): Float = (expectedWidthPx / density * 2f).coerceIn(
    MIN_OVERSIZED_CONTENT_HEIGHT_DP,
    MAX_OVERSIZED_CONTENT_HEIGHT_DP,
)

internal fun shouldApplyRichHtmlMeasurement(
    hasCachedStableHeight: Boolean,
): Boolean = !hasCachedStableHeight

internal fun isConfirmedStableRichHtmlHeight(
    previousHeightCssPx: Int?,
    heightCssPx: Int,
    allImagesSettled: Boolean,
): Boolean = allImagesSettled && previousHeightCssPx == heightCssPx

private fun Context.openExternalUri(uri: Uri): Boolean {
    val scheme = uri.scheme ?: return false
    if (scheme != "http" && scheme != "https" && scheme != "mailto") return false
    return runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }.isSuccess
}

internal const val CONTENT_HEIGHT_SCRIPT =
    """
        (function() {
          var content = document.querySelector('.nodeflow-content');
          if (!content) return '1';
          var rect = content.getBoundingClientRect();
          var style = window.getComputedStyle(content);
          var marginTop = parseFloat(style.marginTop) || 0;
          var marginBottom = parseFloat(style.marginBottom) || 0;
          var height = Math.max(1, Math.ceil(rect.height + marginTop + marginBottom));
          var allImagesSettled = Array.from(content.querySelectorAll('img')).every(function(img) {
            return img.complete;
          });
          return height.toString() + '|' + (allImagesSettled ? '1' : '0');
        })();
    """

internal fun richHtmlImageScript(
    bridgeName: String = NODEFLOW_IMAGE_BRIDGE,
): String =
    """
        (function() {
          if (window.__nodeflowImageManaged) return;
          window.__nodeflowImageManaged = true;
          var content = document.querySelector('.nodeflow-content');
          if (!content) return;

          var ERROR_HTML = '<span class="nf-error-box">' +
            '<svg viewBox="0 0 100 78" fill="none" xmlns="http://www.w3.org/2000/svg">' +
            '<rect x="6" y="8" width="88" height="62" rx="8" stroke="currentColor" stroke-width="5" stroke-linejoin="round" opacity="0.55"/>' +
            '<circle cx="30" cy="27" r="7" fill="currentColor" opacity="0.55"/>' +
            '<path d="M12 62 L38 37 L52 51" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>' +
            '<path d="M46 59 L66 36 L90 58" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round" opacity="0.55"/>' +
            '<path d="M64 10 L57 32 L67 36 L59 62" stroke="currentColor" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/>' +
            '</svg><span class="nf-error-text">图片加载失败，点击重试</span></span>';

          function isCompactImage(img) {
            var className = (img.className || '').toString().toLowerCase();
            return className.indexOf('emoji') >= 0 ||
              className.indexOf('emoticon') >= 0 ||
              className.indexOf('smilie') >= 0 ||
              className.indexOf('smiley') >= 0;
          }

          function notifyResize() {
            if (window.$bridgeName && window.$bridgeName.contentChanged) {
              window.$bridgeName.contentChanged();
            }
          }

          function invalidateLayout() {
            if (window.$bridgeName && window.$bridgeName.layoutInvalidated) {
              window.$bridgeName.layoutInvalidated();
            }
          }

          function originalSrc(img) {
            return img.getAttribute('data-nf-src') || img.getAttribute('src') || '';
          }

          function bust(src) {
            var clean = src.split('#')[0];
            var sep = clean.indexOf('?') >= 0 ? '&' : '?';
            return clean + sep + 'nfretry=' + Date.now();
          }

          function manage(img) {
            if (img.__nfManaged || isCompactImage(img)) return;
            img.__nfManaged = true;
            img.setAttribute('data-nf-src', (img.getAttribute('src') || '').split('#')[0]);
            var wrap = document.createElement('span');
            wrap.className = 'nf-img nf-loading';
            img.parentNode.insertBefore(wrap, img);
            wrap.appendChild(img);

            function markLoaded() { wrap.className = 'nf-img nf-loaded'; notifyResize(); }
            function markError() {
              wrap.className = 'nf-img nf-error';
              if (!wrap.querySelector('.nf-error-box')) {
                wrap.insertAdjacentHTML('beforeend', ERROR_HTML);
              }
              notifyResize();
            }
            function retry() {
              invalidateLayout();
              var box = wrap.querySelector('.nf-error-box');
              if (box) box.parentNode.removeChild(box);
              wrap.className = 'nf-img nf-loading';
              img.setAttribute('src', bust(originalSrc(img)));
              notifyResize();
            }

            img.addEventListener('load', markLoaded);
            img.addEventListener('error', markError);
            wrap.addEventListener('click', function(event) {
              if (wrap.classList.contains('nf-error')) {
                event.preventDefault();
                event.stopPropagation();
                retry();
                return;
              }
              if (wrap.classList.contains('nf-loaded')) {
                event.preventDefault();
                event.stopPropagation();
                if (window.$bridgeName && window.$bridgeName.open) {
                  window.$bridgeName.open(originalSrc(img));
                }
              }
            }, true);

            if (img.complete) {
              if (img.naturalWidth > 0) markLoaded(); else markError();
            }
          }

          content.querySelectorAll('img').forEach(manage);
          notifyResize();
        })();
    """.trimIndent()

private const val NODEFLOW_IMAGE_BRIDGE = "NodeFlowImage"
private const val MAX_COMPOSE_CONSTRAINT_SIZE_PX = 262_142
private const val STABLE_HEIGHT_CONFIRM_DELAY_MILLIS = 160L
private const val MAX_STABLE_HEIGHT_CONFIRM_ATTEMPTS = 4
private const val MIN_OVERSIZED_CONTENT_HEIGHT_DP = 600f
private const val MAX_OVERSIZED_CONTENT_HEIGHT_DP = 2_000f
