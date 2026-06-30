package app.mystery0.nodeflow.core.parser

import app.mystery0.nodeflow.core.model.AccountWealth
import app.mystery0.nodeflow.core.model.DailyCheckIn
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class V2exHtmlParser {
    fun parseTopicList(html: String, sourceNodeName: String? = null): List<Topic> {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("div.cell:has(a.topic-link)")
            .mapNotNull { cell -> parseTopicCell(cell, sourceNodeName) }
    }

    fun parseNodePlanes(html: String): List<NodePlane> {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("div.box:has(div.header):has(div.inner a.item_node)")
            .mapNotNull { box -> parseNodePlane(box) }
    }

    fun parseSignInChallenge(html: String): ParsedSignInChallenge? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val form = document.selectFirst("form[action=/signin]")
            ?: document.selectFirst("form:has(input[type=password][name]):has(input[name=once])")
            ?: return null
        val textInputs = form.select("input[type=text][name]")
        val usernameInput = textInputs.firstOrNull { input ->
            val placeholder = input.attr("placeholder")
            placeholder.contains("username", ignoreCase = true) ||
                placeholder.contains("email", ignoreCase = true) ||
                placeholder.contains("用户名")
        } ?: textInputs.firstOrNull()
        val usernameField = usernameInput
            ?.attr("name")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val passwordField = form.selectFirst("input[type=password][name]")
            ?.attr("name")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val captchaInput = textInputs.lastOrNull { input ->
            val placeholder = input.attr("placeholder")
            placeholder.contains("captcha", ignoreCase = true) ||
                placeholder.contains("code", ignoreCase = true) ||
                placeholder.contains("验证码")
        } ?: textInputs.lastOrNull { it.attr("name") != usernameField }
        val captchaField = captchaInput?.attr("name")?.takeIf { it.isNotBlank() } ?: return null
        val once = form.selectFirst("input[type=hidden][name=once]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val captchaPath = form.selectFirst("img#captcha-image[src], img[src*=_captcha]")
            ?.attr("src")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val next = form.selectFirst("input[type=hidden][name=next]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: "/"
        return ParsedSignInChallenge(
            usernameField = usernameField,
            passwordField = passwordField,
            captchaField = captchaField,
            once = once,
            next = next,
            captchaPath = captchaPath,
        )
    }

    fun parseLoginAccount(html: String): ParsedLoginAccount? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val accountLinks = document
            .select("#Rightbar a[href^=/member/], #Top a[href^=/member/], #Header a[href^=/member/]")
            .takeIf { it.isNotEmpty() }
            ?: document.select("a[href^=/member/]")
        val username = accountLinks
            .mapNotNull { link ->
                link.attr("href")
                    .substringAfter("/member/", missingDelimiterValue = "")
                    .substringBefore("?")
                    .takeIf { it.isNotBlank() }
            }
            .firstOrNull()
            ?: return null
        val avatarUrl = document
            .select("#Rightbar img[src*=avatar/], #Top img[src*=avatar/], #Header img[src*=avatar/], img.avatar[src], img[src*=avatar/]")
            .firstOrNull()
            ?.attr("src")
            ?.normalizeV2exUrl()
            ?.replace("normal.png", "large.png")
        val hasLoggedInMarker = document.select("a[href^=/signout]").isNotEmpty() ||
            document.select("input[onclick*=mission/daily], input[onclick*=balance]").isNotEmpty() ||
            document.text().contains("已连续") ||
            avatarUrl != null
        if (!hasLoggedInMarker) return null
        return ParsedLoginAccount(
            username = username,
            avatarUrl = avatarUrl,
        )
    }

    fun parseLoginProblem(html: String): String? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("div.problem, #problem, .message")
            .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
            .firstOrNull()
    }

    fun parseTwoFactorChallenge(html: String): ParsedTwoFactorChallenge? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val form = document.selectFirst("form[method=post]")
            ?: document.selectFirst("form:has(input[type=hidden][name=once])")
            ?: return null
        val formText = form.text()
        val isTwoFactorForm = formText.contains("两步验证") ||
            formText.contains("two-factor", ignoreCase = true) ||
            formText.contains("two factor", ignoreCase = true) ||
            formText.contains("two-step", ignoreCase = true) ||
            formText.contains("two step", ignoreCase = true)
        if (!isTwoFactorForm) return null
        val once = form.selectFirst("input[type=hidden][name=once]")
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val title = form.selectFirst("tr:first-child, .header, h1")
            ?.text()
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: "两步验证"
        return ParsedTwoFactorChallenge(
            once = once,
            title = title,
        )
    }

    fun parseUnreadNotificationCount(html: String): Int? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val unreadText = document.select("input.super.special.button[value], input[value*=未读], input[value*=unread]")
            .firstOrNull()
            ?.attr("value")
            ?.takeIf { it.isNotBlank() }
        val unreadCount = unreadText?.firstInt()
        if (unreadCount != null) return unreadCount
        val loggedIn = document.select("a[href^=/signout]").isNotEmpty()
        return if (loggedIn) 0 else null
    }

    fun hasSignInEntry(html: String): Boolean {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val hasSignOut = document.select("a[href^=/signout]").isNotEmpty()
        val hasSignIn = document.select("a[href^=/signin], form[action=/signin]").isNotEmpty()
        return hasSignIn && !hasSignOut
    }

    fun parseDailyCheckIn(html: String): DailyCheckIn? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val checkInButton = document
            .select("input[type=button][onclick], input.button[onclick], button[onclick]")
            .firstOrNull { element ->
                val onclick = element.attr("onclick")
                onclick.contains("/mission/daily", ignoreCase = true) ||
                    onclick.contains("/balance", ignoreCase = true)
            }
            ?: return null
        val onclick = checkInButton.attr("onclick")
        val checkedIn = onclick.contains("/balance", ignoreCase = true)
        val redeemOnce = if (checkedIn) {
            null
        } else {
            ONCE_REGEX.find(onclick)?.groupValues?.getOrNull(1)
        }
        val continuousDays = document
            .select("span:contains(已连续), div.cell:contains(已连续)")
            .joinToString(" ") { it.text() }
            .let { CONTINUOUS_DAYS_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
        return DailyCheckIn(
            checkedIn = checkedIn,
            continuousDays = continuousDays,
            redeemOnce = redeemOnce,
        )
    }

    fun parseAccountWealth(html: String): AccountWealth? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val wealth = AccountWealth(
            gold = document.parseCurrencyCount(labels = listOf("金币", "gold"), htmlKeys = listOf("gold")),
            silver = document.parseCurrencyCount(labels = listOf("银币", "silver"), htmlKeys = listOf("silver")),
            bronze = document.parseCurrencyCount(labels = listOf("铜币", "bronze", "copper"), htmlKeys = listOf("bronze", "copper")),
        )
        return wealth.takeIf { it.gold != null || it.silver != null || it.bronze != null }
    }

    private fun Document.parseCurrencyCount(labels: List<String>, htmlKeys: List<String>): Int? {
        outerHtml().currencyCountNearImage(htmlKeys)?.let { return it }
        val candidates = select("tr, div.cell, li, p")
        candidates.firstNotNullOfOrNull { element ->
            val text = element.text()
            labels.firstNotNullOfOrNull { label -> text.numberNearLabel(label) }
        }?.let { return it }
        val pageText = text()
        return labels.firstNotNullOfOrNull { label -> pageText.numberNearLabel(label) }
    }

    private fun String.currencyCountNearImage(htmlKeys: List<String>): Int? {
        val readBeforeImage = CURRENCY_IMAGE_REGEX.find(this)
            ?.let { match -> countBeforeImage(match) != null }
            ?: false
        return htmlKeys.firstNotNullOfOrNull { key ->
            val imageRegex = Regex(
                """<img\b[^>]*${Regex.escape(key)}[^>]*>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
            imageRegex.findAll(this).firstNotNullOfOrNull { match ->
                if (readBeforeImage) countBeforeImage(match) else countAfterImage(match)
            }
        }
    }

    private fun String.countBeforeImage(match: MatchResult): Int? {
        val beforeImage = substring(0, match.range.first)
        val previousTagEnd = beforeImage.lastIndexOf('>').takeIf { it >= 0 }?.plus(1) ?: 0
        return beforeImage
            .substring(previousTagEnd)
            .replace(HTML_TAG_REGEX, " ")
            .lastInt()
    }

    private fun String.countAfterImage(match: MatchResult): Int? {
        val afterImage = substring(match.range.last + 1)
        val nextImageStart = IMAGE_TAG_REGEX
            .find(afterImage)
            ?.range
            ?.first
            ?: afterImage.length
        return afterImage
            .take(nextImageStart)
            .replace(HTML_TAG_REGEX, " ")
            .firstInt()
    }

    private fun String.lastInt(): Int? =
        NUMBER_REGEX.findAll(this).lastOrNull()?.value?.parseFlexibleInt()

    private fun String.numberNearLabel(label: String): Int? {
        val escapedLabel = Regex.escape(label)
        val afterLabel = Regex("""$escapedLabel\s*[:：]?\s*(\d[\d,]*)""", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.parseFlexibleInt()
        if (afterLabel != null) return afterLabel
        return Regex("""(\d[\d,]*)\s*$escapedLabel""", RegexOption.IGNORE_CASE)
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.parseFlexibleInt()
    }

    private fun String.firstInt(): Int? =
        NUMBER_REGEX.find(this)?.value?.parseFlexibleInt()

    private fun String.parseFlexibleInt(): Int? =
        replace(",", "").toIntOrNull()

    private fun String.parseFlexibleLong(): Long? =
        replace(",", "").toLongOrNull()

    fun parseCurrentUsername(html: String): String? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val hasSignOut = document.select("a[href^=/signout]").isNotEmpty()
        if (!hasSignOut) return null
        val accountLinks = document.select("#Rightbar a[href^=/member/], #Top a[href^=/member/], #Header a[href^=/member/]")
            .takeIf { it.isNotEmpty() }
            ?: document.select("a[href^=/member/]")
        return accountLinks
            .mapNotNull { link ->
                link.attr("href")
                    .substringAfter("/member/", missingDelimiterValue = "")
                    .substringBefore("?")
                    .takeIf { it.isNotBlank() }
            }
            .firstOrNull()
    }

    fun isLoggedInAs(html: String, username: String): Boolean =
        parseCurrentUsername(html)?.equals(username, ignoreCase = true) == true

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
        val jsonLdElements = document.parseJsonLdElements()
        return ParsedTopicHtml(
            id = topicId,
            title = title,
            authorName = authorName,
            nodeName = nodeName.orEmpty(),
            nodeTitle = nodeTitle ?: nodeName.orEmpty(),
            contentRendered = contentElement?.html().orEmpty(),
            viewCount = jsonLdElements.firstNotNullOfOrNull { it.interactionCount(VIEW_ACTION) }
                ?: document.parseViewCountFromHeader(),
            hotReplyCount = jsonLdElements
                .mapNotNull { it.likedCommentCount() }
                .firstOrNull { it > 0 }
                ?: document.parseHotReplyCountFromReplyRows(),
            tags = document.select("a.tag[href^=/tag/]")
                .mapNotNull { it.ownText().trim().ifBlank { it.text().trim() }.takeIf(String::isNotBlank) }
                .distinct(),
        )
    }

    fun parseUserProfile(username: String, html: String): User {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val avatar = document.selectFirst("img.avatar")?.attr("src")?.normalizeV2exUrl()
        val bio = document.selectFirst("#Main .box .cell")?.text()?.takeIf { it.isNotBlank() }
        val jsonLdElements = document.parseJsonLdElements()
        val pageText = document.text()
        val memberNumber = jsonLdElements.firstNotNullOfOrNull { element ->
            element.profileIdentifier()?.parseFlexibleLong()
        }
            ?: document.selectFirst("img[data-uid]")?.attr("data-uid")?.parseFlexibleLong()
            ?: MEMBER_NUMBER_REGEX.find(pageText)?.groupValues?.getOrNull(1)?.parseFlexibleLong()
        val dailyActivityRank = document.select("a[href=/top/dau], a[href^=/top/dau]")
            .firstNotNullOfOrNull { link -> link.text().firstInt() }
            ?: DAILY_ACTIVITY_RANK_REGEX.find(pageText)?.groupValues?.getOrNull(1)?.parseFlexibleInt()
        return User(
            username = username,
            avatarUrl = avatar,
            bio = bio,
            memberNumber = memberNumber,
            dailyActivityRank = dailyActivityRank,
        )
    }

    private fun parseNodePlane(box: Element): NodePlane? {
        val header = box.selectFirst("div.header") ?: return null
        val title = header.ownText().trim().takeIf { it.isNotBlank() } ?: return null
        val metadataSpans = header.select("span.flex-one-row.gap5 > span")
        val name = metadataSpans.firstOrNull()?.text()?.trim()?.takeIf { it.isNotBlank() } ?: title
        val nodeCount = NODE_COUNT_REGEX
            .find(header.selectFirst("span.small")?.text().orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        val avatarUrl = header.selectFirst("img[src]")?.attr("src")?.normalizeV2exUrl()
        val nodes = box.select("div.inner a.item_node[href^=/go/]")
            .mapNotNull { link ->
                val nodeName = link.attr("href").substringAfterLast("/").substringBefore("?").trim()
                val nodeTitle = link.text().trim()
                if (nodeName.isBlank() || nodeTitle.isBlank()) {
                    null
                } else {
                    Node(name = nodeName, title = nodeTitle)
                }
            }
            .distinctBy { it.name }
        if (nodes.isEmpty()) return null
        return NodePlane(
            name = name,
            title = title,
            nodeCount = nodeCount,
            avatarUrl = avatarUrl,
            nodes = nodes,
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

    private fun Document.parseJsonLdElements(): List<JsonElement> =
        select("script[type=application/ld+json]").mapNotNull { script ->
            val content = script.data().ifBlank { script.html() }
            runCatching { JsonLdParser.parseToJsonElement(content) }.getOrNull()
        }

    private fun Document.parseViewCountFromHeader(): Int? =
        VIEW_COUNT_REGEX.find(select("small.gray").joinToString(" ") { it.text() })
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(",", "")
            ?.toIntOrNull()

    private fun Document.parseHotReplyCountFromReplyRows(): Int? =
        select("span.small.fade")
            .count { span ->
                span.select("img").any { image ->
                    image.attr("src").contains("heart", ignoreCase = true) ||
                        image.attr("alt").contains("heart", ignoreCase = true) ||
                        image.attr("alt").contains("❤")
                }
            }
            .takeIf { it > 0 }

    private fun JsonElement.interactionCount(interactionType: String): Int? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { it.interactionCount(interactionType) }
        is JsonObject -> get("interactionStatistic")
            .asElementList()
            .firstNotNullOfOrNull { statistic ->
                val statisticObject = statistic as? JsonObject ?: return@firstNotNullOfOrNull null
                val type = statisticObject.stringValue("interactionType")
                statisticObject.intValue("userInteractionCount").takeIf { type == interactionType }
            }
        else -> null
    }

    private fun JsonElement.likedCommentCount(): Int? = when (this) {
        is JsonArray -> mapNotNull { it.likedCommentCount() }.sum().takeIf { it > 0 }
        is JsonObject -> {
            val comments = get("comment").asElementList()
            if (comments.isEmpty()) {
                null
            } else {
                comments.count { comment ->
                    val commentObject = comment as? JsonObject ?: return@count false
                    commentObject.get("interactionStatistic").asElementList().any { statistic ->
                        val statisticObject = statistic as? JsonObject ?: return@any false
                        statisticObject.stringValue("interactionType") == LIKE_ACTION &&
                            (statisticObject.intValue("userInteractionCount") ?: 0) > 0
                    }
                }.takeIf { it > 0 }
            }
        }
        else -> null
    }

    private fun JsonElement.profileIdentifier(): String? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { element -> element.profileIdentifier() }
        is JsonObject -> get("mainEntity")?.profileIdentifier() ?: stringValue("identifier")
        else -> null
    }

    private fun JsonElement?.asElementList(): List<JsonElement> = when (this) {
        is JsonArray -> toList()
        null -> emptyList()
        else -> listOf(this)
    }

    private fun JsonObject.stringValue(key: String): String? =
        (get(key) as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.intValue(key: String): Int? =
        (get(key) as? JsonPrimitive)?.intOrNull

    data class ParsedTopicHtml(
        val id: Long,
        val title: String,
        val authorName: String,
        val nodeName: String,
        val nodeTitle: String,
        val contentRendered: String,
        val viewCount: Int? = null,
        val hotReplyCount: Int? = null,
        val tags: List<String> = emptyList(),
    )

    data class ParsedSignInChallenge(
        val usernameField: String,
        val passwordField: String,
        val captchaField: String,
        val once: String,
        val next: String,
        val captchaPath: String,
    )

    data class ParsedLoginAccount(
        val username: String,
        val avatarUrl: String?,
    )

    data class ParsedTwoFactorChallenge(
        val once: String,
        val title: String,
    )

    private companion object {
        const val V2EX_BASE_URL = "https://www.v2ex.com"
        const val VIEW_ACTION = "https://schema.org/ViewAction"
        const val LIKE_ACTION = "https://schema.org/LikeAction"
        val JsonLdParser = Json { ignoreUnknownKeys = true }
        val TOPIC_ID_REGEX = Regex("""/t/(\d+)""")
        val REPLY_COUNT_REGEX = Regex("""#reply(\d+)""")
        val NODE_COUNT_REGEX = Regex("""(\d+)""")
        val VIEW_COUNT_REGEX = Regex("""(\d[\d,]*)\s+views""")
        val MEMBER_NUMBER_REGEX = Regex("""V2EX\s+member\s+#(\d[\d,]*)""", RegexOption.IGNORE_CASE)
        val DAILY_ACTIVITY_RANK_REGEX = Regex("""Today's activity rank\s+(\d[\d,]*)""", RegexOption.IGNORE_CASE)
        val CONTINUOUS_DAYS_REGEX = Regex("""(\d+)\s*天""")
        val ONCE_REGEX = Regex("""once=(\d+)""")
        val NUMBER_REGEX = Regex("""\d[\d,]*""")
        val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        val IMAGE_TAG_REGEX = Regex("""<img\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val CURRENCY_IMAGE_REGEX = Regex(
            """<img\b[^>]*(gold|silver|bronze|copper)[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    }
}
