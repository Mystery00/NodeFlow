package app.mystery0.nodeflow.core.parser

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/** 将正文中以纯文本出现的 V2EX 主题地址转换为站内链接。 */
internal fun linkifyV2exTopicReferencesHtml(html: String): String {
    if ("/t/" !in html) return html
    val document = Jsoup.parseBodyFragment(html, V2EX_CONTENT_BASE_URL)
    document.body().linkifyPlainV2exTopicLinks()
    return document.body().html()
}

/** 在已经解析的 DOM 中原地识别纯文本主题链接。 */
internal fun Element.linkifyPlainV2exTopicLinks() {
    val textNodes = mutableListOf<TextNode>()
    collectLinkifiableTextNodes(this, textNodes)
    textNodes.forEach(::linkifyTextNode)
}

private fun collectLinkifiableTextNodes(node: Node, out: MutableList<TextNode>) {
    node.childNodes().forEach { child ->
        when {
            child is TextNode -> out += child
            child is Element && child.tagName().lowercase() !in LINKIFY_SKIP_TAGS ->
                collectLinkifiableTextNodes(child, out)
        }
    }
}

private fun linkifyTextNode(textNode: TextNode) {
    val text = textNode.wholeText
    val matches = PLAIN_TOPIC_LINK_REGEX.findAll(text).toList()
    if (matches.isEmpty()) return
    val newNodes = mutableListOf<Node>()
    var lastEnd = 0
    matches.forEach { match ->
        if (match.range.first > lastEnd) newNodes += TextNode(text.substring(lastEnd, match.range.first))
        val topicPath = match.value.substring(match.value.indexOf("/t/"))
        newNodes.add(
            Element("a").apply {
                attr("href", V2EX_CONTENT_BASE_URL + topicPath)
                text(match.value)
            },
        )
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) newNodes += TextNode(text.substring(lastEnd))
    newNodes.forEach { textNode.before(it) }
    textNode.remove()
}

private val PLAIN_TOPIC_LINK_REGEX =
    Regex("""(?<![\w/.])(?:(?:https?://)?(?:www\.)?v2ex\.com)?/t/\d+(?:#reply\d+)?""")
private val LINKIFY_SKIP_TAGS = setOf("a", "pre", "code", "script", "style", "textarea", "button")
private const val V2EX_CONTENT_BASE_URL = "https://www.v2ex.com"
