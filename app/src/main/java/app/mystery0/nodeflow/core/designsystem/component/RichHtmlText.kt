package app.mystery0.nodeflow.core.designsystem.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.roundToInt

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RichHtmlText(
    html: String,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit = {},
) {
    if (html.isBlank()) return
    val context = LocalContext.current
    val currentOnImageClick = rememberUpdatedState(onImageClick)
    val colorScheme = MaterialTheme.colorScheme
    val htmlDocument = remember(html, colorScheme) {
        buildV2exHtmlDocument(
            bodyHtml = html,
            colors = V2exHtmlColors(
                text = colorScheme.onSurface.toCssColor(),
                secondaryText = colorScheme.onSurfaceVariant.toCssColor(),
                link = colorScheme.primary.toCssColor(),
                background = colorScheme.surface.toCssColor(),
                codeBackground = colorScheme.surfaceVariant.copy(alpha = 0.42f).toCssColor(),
                quoteBackground = colorScheme.surfaceVariant.copy(alpha = 0.34f).toCssColor(),
                border = colorScheme.outlineVariant.copy(alpha = 0.72f).toCssColor(),
            ),
        )
    }
    var contentHeight by remember(htmlDocument) { mutableStateOf(1.dp) }

    fun updateContentHeight(view: WebView) {
        view.evaluateJavascript(CONTENT_HEIGHT_SCRIPT) { value ->
            val heightCssPx = value
                ?.trim('"')
                ?.toFloatOrNull()
                ?.roundToInt()
                ?: view.contentHeight
            val nextHeight = webViewCssHeightToDp(heightCssPx)
            if (kotlin.math.abs(nextHeight.value - contentHeight.value) > 1f) {
                contentHeight = nextHeight
            }
        }
    }

    fun scheduleHeightUpdates(view: WebView) {
        listOf(0L, 80L, 240L, 600L, 1200L).forEach { delayMillis ->
            view.postDelayed({ updateContentHeight(view) }, delayMillis)
        }
    }

    fun installImageClickHandler(view: WebView) {
        view.evaluateJavascript(richHtmlImageClickScript(), null)
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight),
        factory = {
            RichHtmlWebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.defaultTextEncodingName = "utf-8"
                settings.loadWithOverviewMode = false
                settings.useWideViewPort = false
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                addJavascriptInterface(
                    RichHtmlImageBridge { url ->
                        post { currentOnImageClick.value(url) }
                    },
                    NODEFLOW_IMAGE_BRIDGE,
                )
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        context.openExternalUri(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        installImageClickHandler(view)
                        scheduleHeightUpdates(view)
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        if (newProgress == 100) {
                            installImageClickHandler(view)
                            scheduleHeightUpdates(view)
                        }
                    }
                }
            }
        },
        update = { view ->
            if (view.tag != htmlDocument) {
                view.tag = htmlDocument
                view.loadDataWithBaseURL(
                    "https://www.v2ex.com/",
                    htmlDocument,
                    "text/html",
                    "utf-8",
                    null,
                )
                installImageClickHandler(view)
                scheduleHeightUpdates(view)
            }
        },
    )
}

private class RichHtmlWebView(context: Context) : WebView(context) {
    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, richHtmlWebViewVerticalScrollY(y))
    }

    override fun onOverScrolled(
        scrollX: Int,
        scrollY: Int,
        clampedX: Boolean,
        clampedY: Boolean,
    ) {
        super.onOverScrolled(
            scrollX,
            richHtmlWebViewVerticalScrollY(scrollY),
            clampedX,
            true,
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post {
            scrollTo(scrollX, 0)
            invalidate()
        }
    }
}

private class RichHtmlImageBridge(
    private val onImageClick: (String) -> Unit,
) {
    @JavascriptInterface
    fun open(url: String) {
        onImageClick(url)
    }
}

internal fun richHtmlWebViewVerticalScrollY(requestedY: Int): Int = 0

private fun Color.toCssColor(): String {
    val argb = toArgb()
    val alpha = android.graphics.Color.alpha(argb) / 255f
    return "rgba(${android.graphics.Color.red(argb)}, ${android.graphics.Color.green(argb)}, ${android.graphics.Color.blue(argb)}, $alpha)"
}

internal fun webViewCssHeightToDp(cssPixels: Int): Dp =
    cssPixels.coerceAtLeast(1).dp

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
          return Math.max(1, Math.ceil(rect.height + marginTop + marginBottom)).toString();
        })();
    """

internal fun richHtmlImageClickScript(
    thresholdPx: Int = ZoomableImageSourceThresholdPx,
): String =
    """
        (function() {
          if (window.__nodeflowImageClickBound) return;
          window.__nodeflowImageClickBound = true;

          function isCompactImage(img) {
            var className = (img.className || '').toString().toLowerCase();
            return className.indexOf('emoji') >= 0 ||
              className.indexOf('emoticon') >= 0 ||
              className.indexOf('smilie') >= 0 ||
              className.indexOf('smiley') >= 0;
          }

          function isBlockElement(node) {
            if (!node || node.nodeType !== 1) return false;
            var tag = node.tagName.toLowerCase();
            return tag === 'br' || tag === 'p' || tag === 'div' ||
              tag === 'li' || tag === 'td' || tag === 'blockquote';
          }

          function hasVisibleText(node) {
            if (!node) return false;
            return ((node.innerText || node.textContent || '').trim().length > 0);
          }

          function hasInlineTextSibling(root, previous) {
            var node = previous ? root.previousSibling : root.nextSibling;
            while (node) {
              if (isBlockElement(node)) return false;
              if (hasVisibleText(node)) return true;
              node = previous ? node.previousSibling : node.nextSibling;
            }
            return false;
          }

          function clickableRoot(img) {
            var parent = img.parentElement;
            if (parent && parent.tagName && parent.tagName.toLowerCase() === 'a') {
              return parent;
            }
            return img;
          }

          document.addEventListener('click', function(event) {
            var target = event.target;
            if (!target) return;
            var img = target.tagName && target.tagName.toLowerCase() === 'img'
              ? target
              : null;
            if (!img && target.closest) {
              var link = target.closest('a');
              img = link ? link.querySelector('img') : null;
            }
            if (!img || isCompactImage(img)) return;

            var width = img.naturalWidth || parseInt(img.getAttribute('width') || '0', 10) || img.clientWidth || 0;
            var height = img.naturalHeight || parseInt(img.getAttribute('height') || '0', 10) || img.clientHeight || 0;
            if (Math.max(width, height) < $thresholdPx) return;

            var root = clickableRoot(img);
            if (hasInlineTextSibling(root, true) || hasInlineTextSibling(root, false)) return;

            var src = img.currentSrc || img.src || img.getAttribute('src');
            if (!src || !window.$NODEFLOW_IMAGE_BRIDGE) return;
            event.preventDefault();
            event.stopPropagation();
            window.$NODEFLOW_IMAGE_BRIDGE.open(src);
          }, true);
        })();
    """.trimIndent()

private const val NODEFLOW_IMAGE_BRIDGE = "NodeFlowImage"
