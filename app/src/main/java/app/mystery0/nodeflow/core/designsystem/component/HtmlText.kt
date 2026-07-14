package app.mystery0.nodeflow.core.designsystem.component

import android.text.method.LinkMovementMethod
import android.text.SpannableStringBuilder
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.text.HtmlCompat
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.ImageRequest
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import kotlin.math.ceil
import kotlin.math.min

@Composable
fun HtmlText(
    html: String,
    modifier: Modifier = Modifier,
    onUrlClick: (String) -> Boolean = { false },
    onImageClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val contentHtml = remember(html) { htmlWithoutImages(html) }
    val images = remember(html) { extractHtmlImageSpecs(html) }
    Column(modifier = modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = {
                TextView(context).apply {
                    movementMethod = LinkMovementMethod.getInstance()
                    textSize = 16f
                    typeface = android.graphics.Typeface.DEFAULT
                    setLineSpacing(0f, 1.12f)
                }
            },
            update = { view ->
                view.setTextColor(textColor)
                view.setLinkTextColor(linkColor)
                val spanned = HtmlCompat.fromHtml(contentHtml, HtmlCompat.FROM_HTML_MODE_COMPACT)
                view.text = spanned.withUrlClickHandler(onUrlClick)
            },
        )
        images.forEach { image ->
            Spacer(Modifier.height(12.dp))
            HtmlImage(
                image = image,
                onImageClick = onImageClick,
            )
        }
    }
}

@Composable
private fun HtmlImage(
    image: HtmlImageSpec,
    modifier: Modifier = Modifier,
    onImageClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var sourceSize by remember(image.url) {
        mutableStateOf(
            image.widthPx?.let { width ->
                image.heightPx?.let { height -> IntSize(width, height) }
            },
        )
    }
    var retryKey by remember(image.url) { mutableStateOf(0) }
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val layoutSize = calculateHtmlImageLayoutSize(
            sourceWidthPx = sourceSize?.width,
            sourceHeightPx = sourceSize?.height,
            maxWidthDp = maxWidth.value,
            compact = image.compact,
        )
        val zoomable = isZoomableHtmlImage(compact = image.compact)
        val request = remember(image.url, retryKey) {
            ImageRequest.Builder(context)
                .data(image.url)
                .setParameter("nodeflowRetry", retryKey)
                .build()
        }
        SubcomposeAsyncImage(
            model = request,
            contentDescription = image.alt,
            modifier = Modifier
                .width(layoutSize.widthDp.dp)
                .height(layoutSize.heightDp.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Fit,
            onState = { state ->
                if (state is AsyncImagePainter.State.Success) {
                    val drawable = state.result.drawable
                    val width = drawable.intrinsicWidth
                    val height = drawable.intrinsicHeight
                    if (width > 0 && height > 0) {
                        sourceSize = IntSize(width, height)
                    }
                }
            },
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading ->
                    ContentImageLoadingPlaceholder(modifier = Modifier.matchParentSize())
                is AsyncImagePainter.State.Error ->
                    ContentImageErrorPlaceholder(
                        onRetry = { retryKey += 1 },
                        modifier = Modifier.matchParentSize(),
                    )
                else -> SubcomposeAsyncImageContent(
                    modifier = if (zoomable) {
                        Modifier.clickable { onImageClick(image.url) }
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

private fun CharSequence.withUrlClickHandler(onUrlClick: (String) -> Boolean): CharSequence {
    val spannable = SpannableStringBuilder(this)
    spannable.getSpans(0, spannable.length, URLSpan::class.java).forEach { span ->
        val start = spannable.getSpanStart(span)
        val end = spannable.getSpanEnd(span)
        val flags = spannable.getSpanFlags(span)
        spannable.removeSpan(span)
        spannable.setSpan(
            object : URLSpan(span.url) {
                override fun onClick(widget: View) {
                    if (!onUrlClick(url)) {
                        super.onClick(widget)
                    }
                }
            },
            start,
            end,
            flags,
        )
    }
    return spannable
}

internal data class HtmlImageSpec(
    val url: String,
    val alt: String?,
    val widthPx: Int?,
    val heightPx: Int?,
    val compact: Boolean,
)

internal data class HtmlImageLayoutSize(
    val widthDp: Float,
    val heightDp: Float,
)

internal fun htmlWithoutImages(html: String): String {
    val document = Jsoup.parseBodyFragment(html, V2EX_BASE_URL)
    document.select("img").forEach { image ->
        val parent = image.parent()
        if (parent != null && parent.tagName().equals("a", ignoreCase = true) && parent.text().isBlank()) {
            parent.remove()
        } else {
            image.remove()
        }
    }
    document.select("p").forEach { paragraph ->
        if (paragraph.text().isBlank() && paragraph.children().isEmpty()) {
            paragraph.remove()
        }
    }
    return document.body().html()
}

internal fun extractHtmlImageSpecs(html: String): List<HtmlImageSpec> {
    val document = Jsoup.parseBodyFragment(html, V2EX_BASE_URL)
    val imageSpecs = document.select("img[src]").mapNotNull { image ->
        val url = image.absUrl("src").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val width = image.imageDimension("width")
        val height = image.imageDimension("height")
        val compactByImage = image.isCompactImage(width, height)
        val compactByPlacement = image.isInlineImage() && !image.isExplicitContentImage()
        HtmlImageSpec(
            url = url,
            alt = image.attr("alt").takeIf { it.isNotBlank() },
            widthPx = width,
            heightPx = height,
            compact = compactByImage || compactByPlacement,
        )
    }
    val linkedImages = document.select("a[href]")
        .filter { link -> link.select("img[src]").isEmpty() }
        .mapNotNull { link ->
            val url = link.absUrl("href").takeIf { it.isImageUrl() } ?: return@mapNotNull null
            HtmlImageSpec(
                url = url,
                alt = link.text().takeIf { it.isNotBlank() },
                widthPx = null,
                heightPx = null,
                compact = false,
            )
        }
    return (imageSpecs + linkedImages).distinctBy { it.url }
}

internal fun calculateHtmlImageLayoutSize(
    sourceWidthPx: Int?,
    sourceHeightPx: Int?,
    maxWidthDp: Float,
    compact: Boolean,
): HtmlImageLayoutSize {
    val sourceWidth = sourceWidthPx?.takeIf { it > 0 }?.toFloat()
    val sourceHeight = sourceHeightPx?.takeIf { it > 0 }?.toFloat()
    val ratio = if (sourceWidth != null && sourceHeight != null) {
        sourceWidth / sourceHeight
    } else {
        DefaultImageAspectRatio
    }
    val maxWidth = if (compact) {
        min(maxWidthDp, CompactImageMaxDp)
    } else {
        maxWidthDp
    }.coerceAtLeast(1f)
    val maxHeight = if (compact) CompactImageMaxDp else ContentImageMaxHeightDp
    var width = min(sourceWidth ?: maxWidth, maxWidth)
    var height = width / ratio
    if (height > maxHeight) {
        height = maxHeight
        width = height * ratio
    }
    return HtmlImageLayoutSize(
        widthDp = ceil(width).coerceAtLeast(1f),
        heightDp = ceil(height).coerceAtLeast(1f),
    )
}

// 只要不是表情/内联小图这类装饰性图片，加载成功后都可以点击查看大图
internal fun isZoomableHtmlImage(compact: Boolean): Boolean = !compact

private fun Element.imageDimension(attributeName: String): Int? =
    attr(attributeName).toCssPixels()
        ?: CSS_DIMENSION_REGEX.findAll(attr("style"))
            .firstOrNull { it.groupValues.getOrNull(1)?.equals(attributeName, ignoreCase = true) == true }
            ?.groupValues
            ?.getOrNull(2)
            ?.toCssPixels()

private fun Element.isInlineImage(): Boolean {
    val root = imageInlineRoot()
    return root.hasInlineTextSibling(previous = true) || root.hasInlineTextSibling(previous = false)
}

private fun Element.isExplicitContentImage(): Boolean =
    classNames().any { className ->
        className.equals(V2EX_EMBEDDED_IMAGE_CLASS, ignoreCase = true)
    }

private fun Element.isCompactImage(width: Int?, height: Int?): Boolean {
    val className = className().lowercase()
    val alt = attr("alt")
    val compactByClass = COMPACT_IMAGE_CLASS_HINTS.any { hint -> className.contains(hint) }
    val compactBySize = width != null && height != null && width <= CompactSourceMaxPx && height <= CompactSourceMaxPx
    val compactByAlt = alt.codePointCount(0, alt.length) in 1..2 && alt.any { !it.isLetterOrDigit() }
    return compactByClass || compactBySize || compactByAlt
}

private fun String.toCssPixels(): Int? =
    trim()
        .removeSuffix("px")
        .toFloatOrNull()
        ?.takeIf { it > 0f }
        ?.let { ceil(it).toInt() }

private fun String.isImageUrl(): Boolean =
    IMAGE_URL_SUFFIXES.any { suffix -> substringBefore('?').lowercase().endsWith(suffix) }

private fun Element.imageInlineRoot(): Node {
    val parent = parent()
    return if (parent != null && parent.tagName().equals("a", ignoreCase = true)) {
        parent
    } else {
        this
    }
}

private fun Node.hasInlineTextSibling(previous: Boolean): Boolean {
    var sibling = if (previous) previousSibling() else nextSibling()
    while (sibling != null) {
        if (sibling.isLineBreakOrBlockElement()) return false
        if (sibling.hasVisibleText()) return true
        sibling = if (previous) sibling.previousSibling() else sibling.nextSibling()
    }
    return false
}

private fun Node.hasVisibleText(): Boolean = when (this) {
    is TextNode -> text().trim().isNotEmpty()
    is Element -> text().trim().isNotEmpty()
    else -> false
}

private fun Node.isLineBreakOrBlockElement(): Boolean =
    this is Element && (tagName().equals("br", ignoreCase = true) || tagName() in BLOCK_TAGS)

private const val V2EX_BASE_URL = "https://www.v2ex.com"
private const val CompactImageMaxDp = 56f
private const val ContentImageMaxHeightDp = 360f
private const val CompactSourceMaxPx = 96
private const val DefaultImageAspectRatio = 16f / 9f
private const val V2EX_EMBEDDED_IMAGE_CLASS = "embedded_image"
private val BLOCK_TAGS = setOf("p", "div", "li", "td", "blockquote")
private val COMPACT_IMAGE_CLASS_HINTS = listOf("emoji", "emoticon", "smilie", "smiley")
private val IMAGE_URL_SUFFIXES = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")
private val CSS_DIMENSION_REGEX = Regex("""(?i)(width|height)\s*:\s*([0-9.]+)px""")
