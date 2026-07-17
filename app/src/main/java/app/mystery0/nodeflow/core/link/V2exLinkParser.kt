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
 *
 * 站内内容里的链接（如回复中的 `<a href="/t/1226857">`）是相对路径，
 * 同样按 v2ex.com 站内地址识别。
 */
object V2exLinkParser {
    private val V2EX_HOSTS = setOf("v2ex.com", "www.v2ex.com")

    fun parse(url: String): V2exLink? {
        val trimmed = url.trim()
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        when (uri.scheme?.lowercase()) {
            "http", "https" -> if (uri.host?.lowercase() !in V2EX_HOSTS) return null
            // 无 scheme 且无 authority 的 /xxx 形式视为站内相对路径；
            // //host/xxx 这类协议相对地址带 authority，不会误入此分支
            null -> if (uri.host != null || uri.authority != null || !trimmed.startsWith("/")) return null
            else -> return null
        }
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
