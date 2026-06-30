package app.mystery0.nodeflow.core.designsystem.component

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
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
) {
    if (html.isBlank()) return
    val context = LocalContext.current
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

    AndroidView(
        modifier = modifier
            .fillMaxWidth()
            .height(contentHeight),
        factory = {
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                settings.javaScriptEnabled = true
                settings.defaultTextEncodingName = "utf-8"
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                        context.openExternalUri(request.url)

                    override fun onPageFinished(view: WebView, url: String?) {
                        scheduleHeightUpdates(view)
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        if (newProgress == 100) {
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
                scheduleHeightUpdates(view)
            }
        },
    )
}

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

private const val CONTENT_HEIGHT_SCRIPT =
    "Math.max(document.body.scrollHeight, document.documentElement.scrollHeight, document.body.offsetHeight, document.documentElement.offsetHeight).toString();"
