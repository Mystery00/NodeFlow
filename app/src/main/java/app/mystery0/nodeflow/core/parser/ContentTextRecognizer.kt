package app.mystery0.nodeflow.core.parser

import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Base64

/** 纯文本增强的统一识别结果；范围为原文中的 UTF-16 偏移，不改变原始内容。 */
internal data class ContentTextMatch(
    val start: Int,
    val end: Int,
    val kind: ContentTextKind,
    val target: String? = null,
)

internal enum class ContentTextKind { Url, Email, Base64 }

internal fun recognizeContentText(text: String, includeBase64: Boolean = true): List<ContentTextMatch> {
    val candidates = mutableListOf<Pair<Int, ContentTextMatch>>()
    fun links(regex: Regex, priority: Int, kind: ContentTextKind, target: (String) -> String) {
        regex.findAll(text).forEach { match ->
            val value = if (kind == ContentTextKind.Url) trimUrlPunctuation(match.value) else match.value
            val safe = safeContentUrl(target(value)) ?: return@forEach
            candidates += priority to ContentTextMatch(match.range.first, match.range.first + value.length, kind, safe)
        }
    }
    links(HTTP_URL, 0, ContentTextKind.Url) { it }
    links(TOPIC_URL, 1, ContentTextKind.Url) { "https://www.v2ex.com" + it.substring(it.indexOf("/t/")) }
    links(EMAIL, 3, ContentTextKind.Email) { "mailto:" + it.replace("%", "%25").replace("?", "%3F").replace("#", "%23") }
    links(WWW_URL, 2, ContentTextKind.Url) { "https://$it" }
    val links = mutableListOf<ContentTextMatch>()
    candidates.sortedWith(compareBy<Pair<Int, ContentTextMatch>> { it.first }.thenByDescending { it.second.end - it.second.start })
        .forEach { (_, match) -> if (links.none { match.overlaps(it) }) links += match }
    if (includeBase64) {
        BASE64_TOKEN.findAll(text).forEach { match ->
            val candidate = ContentTextMatch(match.range.first, match.range.last + 1, ContentTextKind.Base64)
            if (links.none { candidate.overlaps(it) } && decodeReadableBase64(match.value) != null) links += candidate
        }
    }
    return links.sortedBy(ContentTextMatch::start)
}

private fun ContentTextMatch.overlaps(other: ContentTextMatch): Boolean = start < other.end && end > other.start

/** 同时用于识别与实际点击前校验。只接受文字，不接受二进制、宽松 padding 或超长输入。 */
internal fun decodeReadableBase64(encoded: String): String? {
    if (encoded.length !in 12..MAX_BASE64_LENGTH || encoded.length % 4 != 0 || !BASE64_VALUE.matches(encoded)) return null
    // 常见摘要/标识符不能仅因长度碰巧满足而变成可解码小块。
    if (encoded.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    return runCatching {
        val bytes = Base64.getDecoder().decode(encoded)
        if (Base64.getEncoder().encodeToString(bytes) != encoded) return null
        val decoded = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        if (decoded.isBlank() || decoded.any { char ->
                (char.isISOControl() && char !in "\n\r\t") ||
                    Character.getType(char) in setOf(Character.FORMAT.toInt(), Character.PRIVATE_USE.toInt(), Character.UNASSIGNED.toInt())
            }) return null
        decoded
    }.getOrNull()
}

/** 唯一的文字链接协议边界。邮件链接不携带主题/正文等隐式参数。 */
internal fun safeContentUrl(value: String): String? {
    val candidate = value.trim()
    if (candidate.isEmpty() || candidate.any { it.isISOControl() || it == '\\' } || ENCODED_CONTROL.containsMatchIn(candidate)) return null
    val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
    return when (uri.scheme?.lowercase()) {
        "http", "https" -> candidate.takeIf {
            !uri.host.isNullOrBlank() && uri.rawUserInfo == null && uri.port in -1..65535
        }
        "mailto" -> candidate.takeIf {
            uri.rawFragment == null && '?' !in uri.rawSchemeSpecificPart.orEmpty() &&
                EMAIL_VALUE.matches(uri.schemeSpecificPart.orEmpty())
        }
        else -> null
    }
}

private fun trimUrlPunctuation(raw: String): String {
    var value = raw.trimEnd('.', ',', ';', ':', '!', '?', '。', '，', '；', '：', '！', '？', '、', '…')
    val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
    while (value.isNotEmpty()) {
        val close = value.last()
        val open = pairs[close] ?: break
        if (value.count { it == close } <= value.count { it == open }) break
        value = value.dropLast(1).trimEnd('.', ',', ';', ':', '!', '?')
    }
    return value
}

private const val MAX_BASE64_LENGTH = 8192
private const val EMAIL_BODY = "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+"
private val EMAIL_VALUE = Regex(EMAIL_BODY)
private val EMAIL = Regex("(?<![A-Za-z0-9.!#$%&'*+/=?^_`{|}~@-])$EMAIL_BODY(?![A-Za-z0-9_@-])")
private val HTTP_URL = Regex("""(?<![\w@/:])https?://[^\s<>"'，。；！？、（）【】「」『』]+""", RegexOption.IGNORE_CASE)
private val WWW_URL = Regex("""(?<![\w@/:.])www\.[^\s<>"'，。；！？、（）【】「」『』]+""", RegexOption.IGNORE_CASE)
private val TOPIC_URL = Regex("""(?<![\w/.])(?:(?:https?://)?(?:www\.)?v2ex\.com)?/t/\d+(?:#reply\d+)?""")
private val BASE64_TOKEN = Regex("""(?<![A-Za-z0-9_+/=\-])[A-Za-z0-9+/]+={0,2}(?![A-Za-z0-9_+/=\-])""")
private val BASE64_VALUE = Regex("""[A-Za-z0-9+/]+={0,2}""")
private val ENCODED_CONTROL = Regex("%0[0-9a-f]|%1[0-9a-f]|%7f|%5c", RegexOption.IGNORE_CASE)
