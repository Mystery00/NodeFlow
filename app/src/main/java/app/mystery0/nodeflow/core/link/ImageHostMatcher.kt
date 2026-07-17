package app.mystery0.nodeflow.core.link

import java.net.URI

/**
 * 自定义图床域名的归一化与匹配。配置 example.com 时命中自身与任意子域名；
 * 仅 http/https URL 参与匹配。
 */
object ImageHostMatcher {
    // 合法主机名：字母数字与连字符组成的标签，点分隔；单标签（如 localhost）也允许
    private val HOST_REGEX =
        Regex("""^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$""")

    /** 用户输入 → 归一化域名；非法输入返回 null。 */
    fun normalizeHost(input: String): String? {
        val stripped = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .lowercase()
        return stripped.takeIf { it.isNotEmpty() && HOST_REGEX.matches(it) }
    }

    /** URL 主机命中任一配置域名（自身或其子域名）时按图片尝试加载。 */
    fun shouldLoadAsImage(url: String, hosts: Collection<String>): Boolean {
        if (hosts.isEmpty()) return false
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return hosts.any { configured ->
            host == configured || host.endsWith(".$configured")
        }
    }
}
