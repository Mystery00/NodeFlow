package app.mystery0.nodeflow.core.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** 只替换 DOM 文本节点；不对 HTML、属性或既有链接进行字符串替换。 */
internal fun linkifyContentHtml(html: String): String {
    val document = Jsoup.parseBodyFragment(html, "https://www.v2ex.com")
    document.select("a[href]").forEach { anchor ->
        val safe = safeContentUrl(anchor.absUrl("href"))
        if (safe == null) anchor.removeAttr("href") else anchor.attr("href", safe)
    }
    document.body().linkifyPlainContentLinks()
    return document.body().html()
}

internal fun Element.linkifyPlainContentLinks() {
    val nodes = mutableListOf<TextNode>()
    fun collect(node: Node) {
        node.childNodes().forEach { child ->
            when {
                child is TextNode -> nodes += child
                child is Element && child.normalName() !in SKIPPED_TAGS -> collect(child)
            }
        }
    }
    collect(this)
    nodes.forEach { node ->
        val text = node.wholeText
        val matches = recognizeContentText(text, includeBase64 = false)
        if (matches.isEmpty()) return@forEach
        var end = 0
        matches.forEach { match ->
            if (end < match.start) node.before(TextNode(text.substring(end, match.start)))
            node.before(Element("a").attr("data-nodeflow-text-link", "").attr("href", requireNotNull(match.target)).text(text.substring(match.start, match.end)))
            end = match.end
        }
        if (end < text.length) node.before(TextNode(text.substring(end)))
        node.remove()
    }
}

private val SKIPPED_TAGS = setOf("a", "pre", "code", "tt", "script", "style", "textarea", "button", "input")
