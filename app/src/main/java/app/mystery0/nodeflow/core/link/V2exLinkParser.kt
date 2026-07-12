package app.mystery0.nodeflow.core.link

import java.net.URI

sealed interface V2exLink {
    data class Topic(val id: Long) : V2exLink
    data class Node(val name: String) : V2exLink
    data class Member(val username: String) : V2exLink
}

/**
 * 解析 v2ex.com 链接。应用内点击与外部深链共用，能识别的链接在 app 内打开，
 * 返回 null 表示无法识别，应交给浏览器处理。
 */
object V2exLinkParser {
    private val V2EX_HOSTS = setOf("v2ex.com", "www.v2ex.com")

    fun parse(url: String): V2exLink? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "http" && scheme != "https") return null
        if (uri.host?.lowercase() !in V2EX_HOSTS) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotBlank() }
        if (segments.size != 2) return null
        val value = segments[1]
        return when (segments[0]) {
            "t" -> value.toLongOrNull()?.takeIf { it > 0 }?.let { V2exLink.Topic(it) }
            "go" -> V2exLink.Node(value)
            "member" -> V2exLink.Member(value)
            else -> null
        }
    }
}
