package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.isAccessDenied
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.FavoriteTopicsPage
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.ThankResult
import app.mystery0.nodeflow.core.network.V2exHtmlAccessTarget
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.V2exThankApi
import app.mystery0.nodeflow.core.network.accessibleHtmlOrThrow
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.data.common.V2exReplyDto
import app.mystery0.nodeflow.data.common.V2exTopicDto
import app.mystery0.nodeflow.data.common.toReply
import app.mystery0.nodeflow.data.common.toTopic
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class TopicRemoteDataSource(
    private val api: V2exRawApi,
    private val json: Json,
    private val parser: V2exHtmlParser,
    private val thankApi: V2exThankApi? = null,
) {
    suspend fun favoriteTopics(page: Int): FavoriteTopicsPage = safeNetworkCall {
        val response = api.favoriteTopicsHtml(page)
        val url = response.raw().request.url
        val trustedOrigin = url.scheme == "https" && url.host == "www.v2ex.com" && url.port == 443
        if (trustedOrigin && url.encodedPath == "/signin") {
            response.body()?.close()
            throw NodeFlowException(NodeFlowException.Kind.Auth, "登录状态已失效，请重新登录")
        }
        // 复用受限登录表单与 /restricted 分类，不能将受限页解析为空收藏。
        val html = try {
            response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
        } finally {
            response.body()?.close()
            response.errorBody()?.close()
        }
        if (!trustedOrigin || url.encodedPath != "/my/topics" ||
            (url.queryParameter("p")?.toIntOrNull() ?: 1) != page
        ) {
            throw NodeFlowException(NodeFlowException.Kind.Parse, "收藏页面地址异常，请稍后重试")
        }
        if (parser.hasSignInEntry(html)) {
            throw NodeFlowException(NodeFlowException.Kind.Auth, "登录状态已失效，请重新登录")
        }
        if (parser.hasAccessChallenge(html)) {
            throw NodeFlowException(NodeFlowException.Kind.AccessDenied, "V2EX 暂时拒绝访问收藏，请稍后重试")
        }
        parser.parseFavoriteTopicsPage(html, page)
    }

    suspend fun latestTopics(): List<Topic> = homeTopics(HOME_TOPICS_PAGE)

    suspend fun homeTopics(page: Int): List<Topic> = safeNetworkCall {
        val response = if (page == HOME_TOPICS_PAGE) {
            api.allTopicsHtml()
        } else {
            api.recentTopicsHtml(page - HOME_TOPICS_PAGE)
        }
        parser.parseTopicList(
            html = response.bodyStringOrThrow(),
        )
    }

    // 单页抓取：null 表示页面未被识别为主题（调用方决定是否走 JSON 兜底）
    suspend fun topicDetailPage(
        topicId: Long,
        page: Int,
        floorOffset: Int,
    ): V2exHtmlParser.ParsedTopicHtml? = safeNetworkCall {
        parser.parseTopicHtml(
            topicId = topicId,
            html = api.topicHtml(topicId, page = page.takeIf { it > 1 })
                .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic),
            floorOffset = floorOffset,
        )
    }

    suspend fun jsonTopicDetailFallback(topicId: Long): TopicDetail = safeNetworkCall {
        jsonTopicDetail(topicId)
    }

    private suspend fun jsonTopicDetail(topicId: Long): TopicDetail {
        val topic = json.decodeFromString<List<V2exTopicDto>>(api.topic(topicId).bodyStringOrThrow())
            .first()
        val replies = json.decodeFromString<List<V2exReplyDto>>(api.replies(topicId).bodyStringOrThrow())
            .mapIndexed { index, dto -> dto.toReply(topicIdFallback = topicId, floor = index + 1) }
            .withReferencePreviews()
        val supplemental = runCatching {
            parser.parseTopicHtml(
                topicId = topicId,
                html = api.topicHtml(topicId)
                    .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic),
            )
        }.getOrElse { error ->
            if (error is CancellationException || error.isAccessDenied()) throw error
            null
        }
        return TopicDetail(
            topic = topic.toTopic(),
            content = topic.content.orEmpty(),
            contentRendered = topic.contentRendered ?: topic.content.orEmpty(),
            replies = replies,
            viewCount = supplemental?.viewCount,
            hotReplyCount = supplemental?.hotReplyCount,
            tags = supplemental?.tags.orEmpty(),
            isFavorited = supplemental?.isFavorited,
            favoriteOnce = supplemental?.favoriteOnce,
            isThanked = supplemental?.isThanked,
            thankOnce = supplemental?.thankOnce,
            appends = supplemental?.appends.orEmpty(),
        )
    }

    /** 收藏或取消收藏主题。返回操作后页面解析出的最新状态（含新 once token）。 */
    suspend fun setFavorite(
        topicId: Long,
        favorite: Boolean,
        once: String,
    ): V2exHtmlParser.ParsedTopicHtml? = safeNetworkCall {
        val referer = "$V2EX_BASE_URL/t/$topicId"
        val response = if (favorite) {
            api.favoriteTopic(topicId, once, referer)
        } else {
            api.unfavoriteTopic(topicId, once, referer)
        }
        // V2EX 收藏/取消后 302 回主题页；解析响应获取新的收藏状态和 once
        val html = response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
        parser.parseTopicHtml(topicId, html)
    }

    suspend fun thankTopic(topicId: Long, once: String): ThankResult = thank(
        topicId = topicId,
        replyId = null,
        once = once,
    )

    suspend fun thankReply(topicId: Long, replyId: Long, once: String): ThankResult = thank(
        topicId = topicId,
        replyId = replyId,
        once = once,
    )

    private suspend fun thank(topicId: Long, replyId: Long?, once: String): ThankResult = safeNetworkCall {
        val api = requireNotNull(thankApi) { "Thank API is not configured" }
        val response = if (replyId == null) {
            api.thankTopic(topicId, once, "$V2EX_BASE_URL/t/$topicId")
        } else {
            api.thankReply(replyId, once, "$V2EX_BASE_URL/t/$topicId")
        }
        val finalUrl = response.raw().request.url
        val body = response.bodyStringOrThrow()
        if (finalUrl.encodedPath == "/signin" || parser.hasSignInEntry(body)) {
            throw NodeFlowException(NodeFlowException.Kind.Auth, "登录状态已失效，请重新登录")
        }
        if (parser.hasAccessChallenge(body)) {
            throw NodeFlowException(NodeFlowException.Kind.AccessDenied, "V2EX 暂时拒绝访问")
        }
        val result = json.decodeFromString<ThankResultDto>(body).toModel()
        result
    }

    private companion object {
        const val HOME_TOPICS_PAGE = 1
        const val V2EX_BASE_URL = "https://www.v2ex.com"
    }
}

@kotlinx.serialization.Serializable
private data class ThankResultDto(
    val success: Boolean = false,
    val message: String? = null,
    val once: Long? = null,
) {
    fun toModel() = ThankResult(success = success, message = message, once = once?.toString())
}
