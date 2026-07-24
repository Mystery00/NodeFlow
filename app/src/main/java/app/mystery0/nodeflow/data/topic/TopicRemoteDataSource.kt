package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.isAccessDenied
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.network.V2exHtmlAccessTarget
import app.mystery0.nodeflow.core.network.V2exRawApi
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
) {
    suspend fun latestTopics(): List<Topic> = homeTopics(HOME_TOPICS_PAGE)

    suspend fun homeTopics(page: Int): List<Topic> = safeNetworkCall {
        parser.parseTopicList(
            html = api.recentTopicsHtml(page).bodyStringOrThrow(),
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

    private companion object {
        const val HOME_TOPICS_PAGE = 1
        const val V2EX_BASE_URL = "https://www.v2ex.com"
    }
}
