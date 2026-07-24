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
    backgroundColor: Color = Color.Transparent,
    onImageClick: (String) -> Unit = {},
    onUrlClick: (String) -> Boolean = { false },
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

    fun installImageManager(view: WebView) {
        view.evaluateJavascript(richHtmlImageScript(), null)
    }

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight),
        factory = {
            val webView = RichHtmlWebView(context)
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
                    onImageClick = { url -> webView.post { currentOnImageClick.value(url) } },
                    onContentChanged = { webView.post { updateContentHeight(webView) } },
                ),
                NODEFLOW_IMAGE_BRIDGE,
            )
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    currentOnUrlClick.value(request.url.toString()) || context.openExternalUri(request.url)

                override fun onPageFinished(view: WebView, url: String?) {
                    installImageManager(view)
                    scheduleHeightUpdates(view)
                }
            }
            webView.webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (newProgress == 100) {
                        installImageManager(view)
                        scheduleHeightUpdates(view)
                    }
                }
            }
            webView
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
                installImageManager(view)
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
    private val onContentChanged: () -> Unit,
) {
    @JavascriptInterface
    fun open(url: String) {
        onImageClick(url)
    }

    @JavascriptInterface
    fun contentChanged() {
        onContentChanged()
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
