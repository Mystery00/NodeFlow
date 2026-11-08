package app.mystery0.nodeflow.core.parser

import org.jsoup.nodes.Element
import kotlin.math.ceil

/** 正文与回复共用服务端声明的图片尺寸解析。 */
internal fun Element.imageDimension(attributeName: String): Int? =
    attr(attributeName).toCssPixels()
        ?: CSS_DIMENSION_REGEX.findAll(attr("style"))
            .firstOrNull { it.groupValues.getOrNull(1)?.equals(attributeName, ignoreCase = true) == true }
            ?.groupValues?.getOrNull(2)?.toCssPixels()

private fun String.toCssPixels(): Int? =
    trim().lowercase().removeSuffix("px").toFloatOrNull()
        ?.takeIf { it > 0f }
        ?.let { ceil(it).toInt() }

private val CSS_DIMENSION_REGEX = Regex("""(?i)(width|height)\s*:\s*([0-9.]+)px""")
