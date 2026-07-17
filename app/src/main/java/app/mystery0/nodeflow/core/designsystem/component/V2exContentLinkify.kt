package app.mystery0.nodeflow.core.designsystem.component

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * 识别正文/回复里以纯文本出现的站内主题链接（如 `/t/1226857`、`www.v2ex.com/t/1226857`），
 * 转成绝对地址的 `<a>` 标签，点击后由现有 [app.mystery0.nodeflow.core.link.V2exLinkParser]
 * 路由到应用内主题详情。已有链接、代码块内的文本不做处理。
 */
internal fun linkifyV2exTopicReferences(html: String): String {
    if ("/t/" !in html) return html
    val document = Jsoup.parseBodyFragment(html, V2EX_CONTENT_BASE_URL)
    document.body().linkifyPlainV2exTopicLinks()
    return document.body().html()
}

/** 在已解析的内容节点树上原地识别纯文本主题链接。 */
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
        if (match.range.first > lastEnd) {
            newNodes += TextNode(text.substring(lastEnd, match.range.first))
        }
        val topicPath = match.value.substring(match.value.indexOf("/t/"))
        // Element 实现了 Iterable<Element>，用 add 避免 plusAssign 重载歧义
        newNodes.add(
            Element("a").apply {
                attr("href", V2EX_CONTENT_BASE_URL + topicPath)
                text(match.value)
            },
        )
        lastEnd = match.range.last + 1
    }
    if (lastEnd < text.length) {
        newNodes += TextNode(text.substring(lastEnd))
    }
    newNodes.forEach { textNode.before(it) }
    textNode.remove()
}

// 匹配 /t/123、v2ex.com/t/123、https://www.v2ex.com/t/123 等形态；
// 负向后行断言排除路径片段（如 foo/t/123、example.com/t/123）被误识别
private val PLAIN_TOPIC_LINK_REGEX =
    Regex("""(?<![\w/.])(?:(?:https?://)?(?:www\.)?v2ex\.com)?/t/\d+(?:#reply\d+)?""")

// 已是链接或按原样展示的内容不再二次识别
private val LINKIFY_SKIP_TAGS = setOf("a", "pre", "code", "script", "style", "textarea", "button")

private const val V2EX_CONTENT_BASE_URL = "https://www.v2ex.com"
