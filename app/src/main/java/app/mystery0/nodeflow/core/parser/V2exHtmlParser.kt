package app.mystery0.nodeflow.core.parser

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

class V2exHtmlParser {
    fun parseTopicList(html: String, sourceNodeName: String? = null): List<Topic> {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("div.cell:has(a.topic-link)")
            .mapNotNull { cell -> parseTopicCell(cell, sourceNodeName) }
    }

    fun extractImageUrls(html: String): List<String> {
        val document = Jsoup.parseBodyFragment(html, V2EX_BASE_URL)
        val imageSources = document.select("img[src]")
            .mapNotNull { it.attr("src").normalizeV2exUrl() }
        val linkedImages = document.select("a[href]")
            .mapNotNull { it.attr("href").normalizeV2exUrl() }
            .filter { it.isImageUrl() }
        return (imageSources + linkedImages).distinct()
    }

    fun parseTopicHtml(topicId: Long, html: String): ParsedTopicHtml {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val title = document.selectFirst("h1")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "未命名主题"
        val contentElement = document.selectFirst(".topic_content")
        val authorName = document.selectFirst("meta[name=twitter:creator]")?.attr("content")
            ?.removePrefix("@")
            ?.takeIf { it.isNotBlank() }
            ?: document.selectFirst("a[href^=/member/]")?.text()
            ?: ""
        val nodeTitle = document.selectFirst("meta[property=article:section]")?.attr("content")
        val nodeName = document.selectFirst("a[href^=/go/]")?.attr("href")?.substringAfterLast("/")
        return ParsedTopicHtml(
            id = topicId,
            title = title,
            authorName = authorName,
            nodeName = nodeName.orEmpty(),
            nodeTitle = nodeTitle ?: nodeName.orEmpty(),
            contentRendered = contentElement?.html().orEmpty(),
        )
    }

    fun parseUserProfile(username: String, html: String): User {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val avatar = document.selectFirst("img.avatar")?.attr("src")?.normalizeV2exUrl()
        val bio = document.selectFirst("#Main .box .cell")?.text()?.takeIf { it.isNotBlank() }
        return User(
            username = username,
            avatarUrl = avatar,
            bio = bio,
        )
    }

    private fun parseTopicCell(cell: Element, sourceNodeName: String?): Topic? {
        val topicLink = cell.selectFirst("a.topic-link") ?: return null
        val topicId = TOPIC_ID_REGEX.find(topicLink.attr("href"))?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return null
        val title = topicLink.text().trim().takeIf { it.isNotBlank() } ?: return null
        val authorLink = cell.selectFirst(".topic_info strong a[href^=/member/]")
            ?: cell.selectFirst("a[href^=/member/]")
        val username = authorLink?.text()?.trim().orEmpty()
        val avatarUrl = cell.selectFirst("img.avatar")?.attr("src")?.normalizeV2exUrl()
        val replyCount = cell.selectFirst("a.count_livid, a.count_orange")?.text()?.trim()?.toIntOrNull()
            ?: REPLY_COUNT_REGEX.find(topicLink.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 0
        val lastReplyBy = cell.select(".topic_info strong a[href^=/member/]").drop(1).firstOrNull()?.text()
        val createdText = cell.selectFirst(".topic_info span[title]")?.attr("title")

        val nodeName = sourceNodeName
            ?: cell.selectFirst(".topic_info a[href^=/go/]")?.attr("href")?.substringAfterLast("/")
            ?: ""
        val nodeTitle = cell.selectFirst(".topic_info a[href^=/go/]")?.text()?.takeIf { it.isNotBlank() }
            ?: nodeName

        return Topic(
            id = topicId,
            title = title,
            url = "$V2EX_BASE_URL/t/$topicId",
            node = Node(name = nodeName, title = nodeTitle),
            author = User(username = username, avatarUrl = avatarUrl),
            avatarUrl = avatarUrl,
            replyCount = replyCount,
            lastReplyBy = lastReplyBy,
            createdAtEpochSeconds = null,
            lastTouchedAtEpochSeconds = null,
        )
    }

    private fun String.normalizeV2exUrl(): String? {
        val raw = trim()
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("//") -> "https:$raw"
            raw.startsWith("/") -> "$V2EX_BASE_URL$raw"
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> null
        }
    }

    private fun String.isImageUrl(): Boolean {
        val path = substringBefore("?").lowercase()
        return path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".gif") ||
            path.endsWith(".webp") ||
            path.endsWith(".avif")
    }

    data class ParsedTopicHtml(
        val id: Long,
        val title: String,
        val authorName: String,
        val nodeName: String,
        val nodeTitle: String,
        val contentRendered: String,
    )

    private companion object {
        const val V2EX_BASE_URL = "https://www.v2ex.com"
        val TOPIC_ID_REGEX = Regex("""/t/(\d+)""")
        val REPLY_COUNT_REGEX = Regex("""#reply(\d+)""")
    }
}
