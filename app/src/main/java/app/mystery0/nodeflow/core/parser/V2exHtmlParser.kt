package app.mystery0.nodeflow.core.parser

import app.mystery0.nodeflow.core.model.AccountWealth
import app.mystery0.nodeflow.core.model.DailyCheckIn
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.model.NotificationReferenceLocator
import app.mystery0.nodeflow.core.model.ProfileReply
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.TextNode
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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

    fun parseNodeDetail(name: String, html: String): Node? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val jsonLdElements = document.parseJsonLdElements()
        val parsedTitle = jsonLdElements.firstNotNullOfOrNull { it.nodePageName() }
            ?: document.selectFirst("meta[property=og:title], meta[name=twitter:title]")?.attr("content")
            ?: document.selectFirst("h1")?.text()
        val header = jsonLdElements.firstNotNullOfOrNull { it.nodePageDescription() }
            ?: document.selectFirst("meta[name=description], meta[property=og:description]")
                ?.attr("content")
                ?.htmlToPlainText()
        val avatarUrl = jsonLdElements.firstNotNullOfOrNull { it.nodePageImage() }
            ?: document.selectFirst("meta[property=og:image], meta[name=twitter:image]")
                ?.attr("content")
                ?.normalizeV2exUrl()
            ?: document.selectFirst("img[src*=navatar]")
                ?.attr("src")
                ?.normalizeV2exUrl()
        val topics = jsonLdElements.firstNotNullOfOrNull { it.nodePageTopicCount() }
        if (parsedTitle.isNullOrBlank() && header.isNullOrBlank() && avatarUrl.isNullOrBlank() && topics == null) {
            return null
        }
        return Node(
            name = name,
            title = parsedTitle?.trim()?.ifBlank { name } ?: name,
            header = header,
            avatarUrl = avatarUrl,
            topics = topics,
        )
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

    fun parseDailyCheckIn(html: String): DailyCheckIn? = parseDailyCheckInPage(html)?.checkIn

    internal fun parseDailyCheckInPage(html: String): ParsedDailyCheckInPage? {
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
        return ParsedDailyCheckInPage(
            checkIn = DailyCheckIn(
                checkedIn = checkedIn,
                continuousDays = continuousDays,
                canCheckIn = !checkedIn && redeemOnce != null,
            ),
            redeemOnce = redeemOnce,
        )
    }

    fun isDailyCheckInSuccess(html: String): Boolean {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val hasRedeemButton = document.select("[onclick*=mission/daily/redeem]").isNotEmpty()
        val hasBalanceButton = document.select("[onclick*=balance]").isNotEmpty()
        val hasPositiveMarker = DAILY_CHECK_IN_SUCCESS_MARKERS.any(document.text()::contains)
        return !hasRedeemButton && hasBalanceButton && hasPositiveMarker
    }

    fun hasDailyCheckInRiskNotice(html: String): Boolean {
        return hasAccessChallenge(html)
    }

    fun hasAccessChallenge(html: String): Boolean {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        // 正常业务正文可能讨论 Cloudflare 等关键词，已有通知结构时不能据此误判整页。
        if (document.select("#Main .cell[id^=n_]").isNotEmpty()) return false
        val pageContent = "${document.text()} ${document.outerHtml()}".lowercase()
        return DAILY_CHECK_IN_RISK_MARKERS.any(pageContent::contains)
    }

    fun parseLatestDailyReward(html: String): Int? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("tr")
            .mapNotNull { row ->
                val cells = row.select("th, td")
                val descriptionIndex = cells.indexOfFirst { it.text().contains("每日登录") }
                if (descriptionIndex < 0) return@mapNotNull null
                cells.getOrNull(descriptionIndex + 1)
                    ?.text()
                    ?.let { DAILY_REWARD_REGEX.find(it)?.groupValues?.getOrNull(1) }
                    ?.replace(",", "")
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
            }
            .firstOrNull()
    }

    fun parseNotifications(html: String): List<Notification> {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("#Main .cell[id^=n_]").mapNotNull(::parseNotification)
    }

    fun isNotificationsPage(html: String): Boolean {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        val notificationCells = document.select("#Main .cell[id^=n_]")
        if (notificationCells.isNotEmpty()) {
            return notificationCells.all { parseNotification(it) != null }
        }
        val title = document.title()
        return document.selectFirst("#Main .box") != null &&
            (title.contains("提醒系统", ignoreCase = true) ||
                title.contains("notifications", ignoreCase = true))
    }

    private fun parseNotification(cell: Element): Notification? {
        val id = cell.id().removePrefix("n_").toLongOrNull() ?: return null
        val topicLink = cell.selectFirst("a.topic-link[href^=/t/]") ?: return null
        val topicMatch = NOTIFICATION_TOPIC_REGEX.find(topicLink.attr("href")) ?: return null
        val topicId = topicMatch.groupValues.getOrNull(1)?.toLongOrNull() ?: return null
        val replyFloor = topicMatch.groupValues.getOrNull(2)?.toIntOrNull()
        val actorLink = cell.selectFirst(".fade a[href^=/member/]")
            ?: cell.selectFirst("a[href^=/member/]")
            ?: return null
        val username = actorLink.attr("href")
            .substringAfter("/member/", missingDelimiterValue = "")
            .substringBefore('?')
            .takeIf(String::isNotBlank)
            ?: return null
        val action = cell.selectFirst(".fade")
            ?.clone()
            ?.also { fade -> fade.select("a").remove() }
            ?.text()
            ?.replace(Regex("""\s*›\s*"""), "")
            ?.replace(Regex("""\s+"""), " ")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?: return null
        val payload = cell.selectFirst(".payload")
        val contentRendered = payload?.html()?.trim()?.takeIf(String::isNotBlank)
        val referenceLocator = payload?.text()?.let { text ->
            NOTIFICATION_REFERENCE_REGEX.find(text)?.let { match ->
                val referenceUsername = match.groupValues.getOrNull(1)?.takeIf(String::isNotBlank)
                val floor = match.groupValues.getOrNull(2)?.toIntOrNull()
                if (referenceUsername != null && floor != null) {
                    NotificationReferenceLocator(username = referenceUsername, floor = floor)
                } else {
                    null
                }
            }
        }
        return Notification(
            id = id,
            actor = User(
                username = username,
                avatarUrl = cell.selectFirst("img.avatar[src]")?.attr("src")?.normalizeV2exUrl(),
            ),
            action = action,
            topicId = topicId,
            topicTitle = topicLink.text().trim(),
            replyFloor = replyFloor,
            relativeTime = cell.selectFirst(".snow")?.text()?.trim().orEmpty(),
            contentRendered = contentRendered,
            referenceLocator = referenceLocator,
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

    fun parseReplyForm(
        topicId: Long,
        html: String,
        baseUrl: String = V2EX_BASE_URL,
    ): ParsedReplyForm? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        if (document.hasRestrictedSignInForm() || hasAccessChallenge(html)) return null
        val expectedAction = "${baseUrl.trimEnd('/')}/t/$topicId"
        val form = document.select("form[method]").firstOrNull { form ->
            form.attr("method").equals("post", ignoreCase = true) &&
                form.absUrl("action") == expectedAction &&
                form.selectFirst("textarea[name]") != null
        } ?: return null
        val textarea = form.selectFirst("textarea[name]") ?: return null
        return ParsedReplyForm(
            actionUrl = expectedAction,
            contentField = textarea.attr("name"),
            maxLength = textarea.attr("maxlength")
                .toIntOrNull()
                ?.takeIf { it > 0 }
                ?: DEFAULT_REPLY_MAX_LENGTH,
            hiddenFields = form.select("input[type=hidden][name]")
                .associate { input -> input.attr("name") to input.attr("value") },
        )
    }

    fun parseImageUploadPage(html: String): ParsedImageUploadPage {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return when {
            hasSignInEntry(html) || document.selectFirst("form[action=/signin]") != null ->
                ParsedImageUploadPage.AuthenticationRequired
            document.selectFirst("form[action=/i/upload] input[type=file][name=qqfile]") != null ->
                ParsedImageUploadPage.Available
            else -> ParsedImageUploadPage.PermissionDenied
        }
    }

    fun parseImageUploadResponse(body: String): ParsedImageUploadResponse? {
        val root = runCatching {
            JsonLdParser.parseToJsonElement(body) as? JsonObject
        }.getOrNull() ?: return null
        val successValue = root["success"] as? JsonPrimitive ?: return null
        val succeeded = successValue.booleanOrNull == true ||
            successValue.contentOrNull.equals("true", ignoreCase = true)
        if (!succeeded) return null
        val imageId = root.stringValue("name") ?: return null
        val uri = root.stringValue("uri") ?: return null
        val originalUrl = root.stringValue("url_o")
            ?.normalizeV2exUrl()
            ?.takeIf { url ->
                runCatching {
                    val parsed = java.net.URI(url)
                    parsed.scheme == "https" && parsed.host == "i.v2ex.co"
                }.getOrDefault(false)
            }
            ?: return null
        return ParsedImageUploadResponse(
            imageId = imageId,
            originalUrl = originalUrl,
            detailUrl = "$V2EX_BASE_URL/i/$uri",
        )
    }

    fun parseV2exProblem(html: String): String? = Jsoup.parse(html, V2EX_BASE_URL)
        .select(".problem, #problem")
        .mapNotNull { it.text().trim().takeIf(String::isNotBlank) }
        .firstOrNull()

    fun parseTopicHtml(topicId: Long, html: String, floorOffset: Int = 0): ParsedTopicHtml? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        if (document.hasRestrictedSignInForm()) return null
        val contentElement = document.selectFirst("#Main .topic_content")
            ?: document.select(".topic_content")
                .firstOrNull { element -> element.parents().none { it.id() == "node_sidebar" } }
        val replyElements = document.select("div[id]").filter { REPLY_ROW_ID_REGEX.matches(it.id()) }
        val topicHeader = document.select(".header:has(h1)")
            .firstOrNull { header -> header.selectFirst("a[href^=/member/]") != null }
        // 标题型主题可以既没有正文也没有回复；登录页、404 等页面还必须排除带作者的主题头。
        if (contentElement == null && replyElements.isEmpty() && topicHeader == null) return null
        val title = document.selectFirst("h1")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "未命名主题"
        // 注意：meta[name=twitter:creator] 是 V2EX 站点账号（@V2EX），不是发帖人，不能用
        val authorName = document.selectFirst("small.gray a[href^=/member/]")?.text()?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: document.select("a[href^=/member/]").firstOrNull { it.text().isNotBlank() }?.text()?.trim()
            ?: ""
        val authorAvatarUrl = document.selectFirst(".header img.avatar")?.attr("src")?.normalizeV2exUrl()
        val nodeTitle = document.selectFirst("meta[property=article:section]")?.attr("content")
        val nodeName = document.selectFirst("a[href^=/go/]")?.attr("href")?.substringAfterLast("/")
        val createdAt = document.selectFirst(".header small.gray span[title]")
            ?.attr("title")
            ?.parseV2exDateTime()
        val jsonLdElements = document.parseJsonLdElements()
        val replies = replyElements.parseTopicReplies(topicId, floorOffset)
        val pageCount = document.selectFirst("input.page_input")
            ?.attr("max")
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: 1
        // 解析收藏/取消收藏链接
        val favoriteLink = document.selectFirst("a[href^=/unfavorite/topic/$topicId]")
        val unfavoriteLink = document.selectFirst("a[href^=/favorite/topic/$topicId]")
        val isFavorited: Boolean?
        val favoriteOnce: String?
        when {
            favoriteLink != null -> {
                isFavorited = true
                favoriteOnce = ONCE_REGEX.find(favoriteLink.attr("href"))?.groupValues?.get(1)
            }
            unfavoriteLink != null -> {
                isFavorited = false
                favoriteOnce = ONCE_REGEX.find(unfavoriteLink.attr("href"))?.groupValues?.get(1)
            }
            else -> {
                isFavorited = null
                favoriteOnce = null
            }
        }
        return ParsedTopicHtml(
            id = topicId,
            title = title,
            authorName = authorName,
            authorAvatarUrl = authorAvatarUrl,
            nodeName = nodeName.orEmpty(),
            nodeTitle = nodeTitle ?: nodeName.orEmpty(),
            contentRendered = contentElement?.html().orEmpty(),
            createdAtEpochSeconds = createdAt,
            viewCount = jsonLdElements.firstNotNullOfOrNull { it.interactionCount(VIEW_ACTION) }
                ?: document.parseViewCountFromHeader(),
            hotReplyCount = jsonLdElements
                .mapNotNull { it.likedCommentCount() }
                .firstOrNull { it > 0 }
                ?: document.parseHotReplyCountFromReplyRows(),
            tags = document.select("a.tag[href^=/tag/]")
                .mapNotNull { it.ownText().trim().ifBlank { it.text().trim() }.takeIf(String::isNotBlank) }
                .distinct(),
            replyCount = document.select("div.cell span.gray")
                .firstNotNullOfOrNull { TOTAL_REPLY_COUNT_REGEX.find(it.text())?.groupValues?.get(1)?.toIntOrNull() },
            pageCount = pageCount,
            replies = replies,
            isFavorited = isFavorited,
            favoriteOnce = favoriteOnce,
        )
    }

    private fun List<Element>.parseTopicReplies(topicId: Long, floorOffset: Int): List<Reply> =
        mapIndexedNotNull { index, element ->
            val id = element.id().removePrefix("r_").toLongOrNull() ?: return@mapIndexedNotNull null
            val contentElement = element.selectFirst(".reply_content")
            val contentRendered = contentElement?.html().orEmpty()
            val contentText = contentElement?.replyPlainText().orEmpty()
            // 兜底楼层带上页偏移，保证第 2 页起 span.no 缺失时不会从 1 重新计数
            val floor = element.selectFirst("span.no")?.text()?.firstInt() ?: (floorOffset + index + 1)
            val username = element.selectFirst("strong a[href^=/member/]")?.text()?.trim()
                ?: element.selectFirst("a[href^=/member/]")?.text()?.trim()
                ?: ""
            val avatarUrl = element.selectFirst("img.avatar")?.attr("src")?.normalizeV2exUrl()
            val createdAt = element.selectFirst("span.ago[title], span[title]")
                ?.attr("title")
                ?.parseV2exDateTime()
            Reply(
                id = id,
                topicId = topicId,
                floor = floor,
                author = User(username = username, avatarUrl = avatarUrl),
                content = contentText,
                contentRendered = contentRendered.ifBlank { contentText },
                createdAtEpochSeconds = createdAt,
                thanks = element.parseReplyThanks(),
            )
        }

    private fun Element.replyPlainText(): String {
        val clone = clone()
        clone.select("a:has(img[src])").forEach { anchor ->
            val image = anchor.selectFirst("img[src]") ?: return@forEach
            val url = anchor.attr("href").normalizeV2exUrl()
                ?: image.attr("src").normalizeV2exUrl()
                ?: return@forEach
            anchor.replaceWith(TextNode(url))
        }
        clone.select("img[src]").forEach { image ->
            val url = image.attr("src").normalizeV2exUrl() ?: return@forEach
            image.replaceWith(TextNode(url))
        }
        return clone.text()
    }

    private fun Element.parseReplyThanks(): Int =
        select("span.small.fade")
            .firstOrNull { span ->
                span.select("img").any { image ->
                    image.attr("src").contains("heart", ignoreCase = true) ||
                        image.attr("alt").contains("heart", ignoreCase = true) ||
                        image.attr("alt").contains("❤")
                }
            }
            ?.ownText()
            ?.firstInt()
            ?: 0

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

    fun parseUserRecentTopics(html: String): List<Topic> {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        // 会员页「最近主题」区块的每条主题是带 topic-link 的 div.cell.item
        return document.select("div.cell.item:has(a.topic-link)")
            .mapNotNull { cell -> parseTopicCell(cell, sourceNodeName = null) }
    }

    fun parseUserRecentReplies(html: String): List<ProfileReply> {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        // 会员页「最近回复」区块：每条回复是 div.dock_area（元信息）+ 紧随的 div.inner .reply_content
        return document.select("div.dock_area").mapNotNull { dock ->
            val topicLink = dock.select("a[href*=/t/]").lastOrNull() ?: return@mapNotNull null
            val topicId = TOPIC_ID_REGEX.find(topicLink.attr("href"))
                ?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: return@mapNotNull null
            val topicTitle = topicLink.text().trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val contentElement = dock.nextElementSibling()
                ?.takeIf { it.hasClass("inner") }
                ?.selectFirst(".reply_content")
                ?: return@mapNotNull null
            val nodeLink = dock.selectFirst("a[href^=/go/]")
            val nodeName = nodeLink?.attr("href")?.substringAfterLast("/").orEmpty()
            val nodeTitle = nodeLink?.text()?.trim()?.takeIf { it.isNotBlank() } ?: nodeName
            val createdAt = dock.selectFirst("span.fade[title], span[title]")
                ?.attr("title")
                ?.parseV2exDateTime()
            val contentRendered = contentElement.html()
            val contentText = contentElement.text()
            ProfileReply(
                topicId = topicId,
                topicTitle = topicTitle,
                nodeName = nodeName,
                nodeTitle = nodeTitle,
                content = contentText,
                contentRendered = contentRendered.ifBlank { contentText },
                createdAtEpochSeconds = createdAt,
            )
        }
    }

    /** 在记事本列表页中定位 V2EX_Polish 数据 note，返回 note id；找不到返回 null。 */
    fun parsePolishNoteId(html: String): Long? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("a[href^=/notes/]")
            .firstOrNull { it.text().startsWith(PolishMemberTagParser.NOTE_PREFIX) }
            ?.attr("href")
            ?.substringAfterLast('/')
            ?.toLongOrNull()
    }

    /** 提取 note 编辑页 textarea 的完整原文（Jsoup 已做实体解码）；无 textarea 返回 null。 */
    fun parseNoteEditContent(html: String): String? =
        Jsoup.parse(html, V2EX_BASE_URL)
            .selectFirst("textarea")
            ?.wholeText()
            ?.takeIf { it.isNotBlank() }

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
        val memberNames = cell.select("strong a[href^=/member/]")
            .mapNotNull { link -> link.text().trim().takeIf { it.isNotBlank() } }
        val username = memberNames.firstOrNull()
            ?: cell.select("a[href^=/member/]")
                .mapNotNull { link -> link.text().trim().takeIf { it.isNotBlank() } }
                .firstOrNull()
            ?: cell.selectFirst("img.avatar[alt]")?.attr("alt")?.trim().orEmpty()
        val avatarUrl = cell.selectFirst("img.avatar")?.attr("src")?.normalizeV2exUrl()
        val replyCount = cell.selectFirst("a.count_livid, a.count_orange")?.text()?.trim()?.toIntOrNull()
            ?: REPLY_COUNT_REGEX.find(topicLink.attr("href"))?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: 0
        val lastReplyBy = memberNames.drop(1).firstOrNull()
        val touchedAtEpochSeconds = cell.selectFirst(".topic_info span[title]")
            ?.attr("title")
            ?.parseV2exDateTime()

        val nodeLink = cell.selectFirst(".topic_info a.node[href^=/go/], .topic_info a[href^=/go/], a.node[href^=/go/]")
        val nodeName = sourceNodeName
            ?: nodeLink?.attr("href")?.substringAfterLast("/")
            ?: ""
        val nodeTitle = nodeLink?.text()?.takeIf { it.isNotBlank() }
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
            lastTouchedAtEpochSeconds = touchedAtEpochSeconds,
        )
    }

    private fun String.parseV2exDateTime(): Long? =
        try {
            OffsetDateTime.parse(trim(), V2EX_DATE_TIME_FORMATTER).toEpochSecond()
        } catch (_: DateTimeParseException) {
            null
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

    private fun JsonElement.nodePageName(): String? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { element -> element.nodePageName() }
        is JsonObject -> stringValue("name")
            ?: get("@graph")?.nodePageName()
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun JsonElement.nodePageDescription(): String? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { element -> element.nodePageDescription() }
        is JsonObject -> stringValue("description")
            ?.htmlToPlainText()
            ?: get("@graph")?.nodePageDescription()
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun JsonElement.nodePageImage(): String? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { element -> element.nodePageImage() }
        is JsonObject -> get("image").imageUrl()
            ?: get("@graph")?.nodePageImage()
        else -> null
    }?.normalizeV2exUrl()

    private fun JsonElement.nodePageTopicCount(): Int? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { element -> element.nodePageTopicCount() }
        is JsonObject -> get("mainEntity").itemListNumberOfItems()
            ?: get("@graph")?.nodePageTopicCount()
        else -> null
    }

    private fun JsonElement?.imageUrl(): String? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { element -> element.imageUrl() }
        is JsonObject -> stringValue("url") ?: stringValue("contentUrl")
        is JsonPrimitive -> contentOrNull
        null -> null
    }?.takeIf { it.isNotBlank() }

    private fun JsonElement?.itemListNumberOfItems(): Int? = when (this) {
        is JsonArray -> firstNotNullOfOrNull { element -> element.itemListNumberOfItems() }
        is JsonObject -> flexibleIntValue("numberOfItems")
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
        flexibleIntValue(key)

    private fun JsonObject.flexibleIntValue(key: String): Int? {
        val value = get(key) as? JsonPrimitive ?: return null
        return value.intOrNull ?: value.contentOrNull?.parseFlexibleInt()
    }

    private fun String.htmlToPlainText(): String =
        Jsoup.parseBodyFragment(this, V2EX_BASE_URL).text().trim()

    data class ParsedTopicHtml(
        val id: Long,
        val title: String,
        val authorName: String,
        val authorAvatarUrl: String? = null,
        val nodeName: String,
        val nodeTitle: String,
        val contentRendered: String,
        val createdAtEpochSeconds: Long? = null,
        val viewCount: Int? = null,
        val hotReplyCount: Int? = null,
        val tags: List<String> = emptyList(),
        val replyCount: Int? = null,
        val pageCount: Int = 1,
        val replies: List<Reply> = emptyList(),
        val isFavorited: Boolean? = null,
        val favoriteOnce: String? = null,
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

    data class ParsedReplyForm(
        val actionUrl: String,
        val contentField: String,
        val maxLength: Int,
        val hiddenFields: Map<String, String>,
    )

    enum class ParsedImageUploadPage {
        Available,
        AuthenticationRequired,
        PermissionDenied,
    }

    data class ParsedImageUploadResponse(
        val imageId: String,
        val originalUrl: String,
        val detailUrl: String,
    )

    private companion object {
        const val V2EX_BASE_URL = "https://www.v2ex.com"
        const val DEFAULT_REPLY_MAX_LENGTH = 10_000
        const val VIEW_ACTION = "https://schema.org/ViewAction"
        const val LIKE_ACTION = "https://schema.org/LikeAction"
        val JsonLdParser = Json { ignoreUnknownKeys = true }
        val TOPIC_ID_REGEX = Regex("""/t/(\d+)""")
        val REPLY_COUNT_REGEX = Regex("""#reply(\d+)""")
        val REPLY_ROW_ID_REGEX = Regex("""r_\d+""")
        val TOTAL_REPLY_COUNT_REGEX = Regex("""(\d+)\s*条回复""")
        val NODE_COUNT_REGEX = Regex("""(\d+)""")
        val VIEW_COUNT_REGEX = Regex("""(\d[\d,]*)\s+views""")
        val MEMBER_NUMBER_REGEX = Regex("""V2EX\s+member\s+#(\d[\d,]*)""", RegexOption.IGNORE_CASE)
        val DAILY_ACTIVITY_RANK_REGEX = Regex("""Today's activity rank\s+(\d[\d,]*)""", RegexOption.IGNORE_CASE)
        val CONTINUOUS_DAYS_REGEX = Regex("""(\d+)\s*天""")
        val ONCE_REGEX = Regex("""once=(\d+)""")
        val DAILY_REWARD_REGEX = Regex("""\+?(\d[\d,]*)""")
        val NOTIFICATION_TOPIC_REGEX = Regex("""^/t/(\d+)(?:#reply(\d+))?""")
        val NOTIFICATION_REFERENCE_REGEX =
            Regex("""(?:^|\s)@([A-Za-z0-9_][A-Za-z0-9_-]{0,31})\s*#(\d{1,4})(?=$|[^A-Za-z0-9_-])""")
        val DAILY_CHECK_IN_SUCCESS_MARKERS = listOf("每日登录奖励已领取", "已领取", "成功领取", "已成功")
        val DAILY_CHECK_IN_RISK_MARKERS = listOf(
            "干净安装的浏览器",
            "clean browser",
            "just a moment",
            "cf-chl",
            "cf-turnstile",
            "challenge-platform",
            "attention required",
            "cloudflare",
        )
        val NUMBER_REGEX = Regex("""\d[\d,]*""")
        val HTML_TAG_REGEX = Regex("""<[^>]+>""")
        val IMAGE_TAG_REGEX = Regex("""<img\b""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val CURRENCY_IMAGE_REGEX = Regex(
            """<img\b[^>]*(gold|silver|bronze|copper)[^>]*>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        val V2EX_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss xxx")
    }
}

internal data class ParsedDailyCheckInPage(
    val checkIn: DailyCheckIn,
    val redeemOnce: String?,
)
