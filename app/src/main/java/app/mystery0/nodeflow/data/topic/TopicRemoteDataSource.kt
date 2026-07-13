package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.isAccessDenied
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
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

    suspend fun topicDetail(topicId: Long): TopicDetail = safeNetworkCall {
        // 主线路：网页 HTML，不消耗旧 JSON API 的 IP 限流配额，并支持登录可见内容
        val htmlDetail = runCatching {
            htmlTopicDetail(topicId)
        }.getOrElse { error ->
            if (error is CancellationException || error.isAccessDenied()) throw error
            null
        }
        if (htmlDetail != null) {
            htmlDetail
        } else {
            // 兜底线路：网页未被识别为主题（结构变更 / 非预期页面）时回退到旧 JSON API
            jsonTopicDetail(topicId)
        }
    }

    private suspend fun htmlTopicDetail(topicId: Long): TopicDetail? {
        val firstPage = parser.parseTopicHtml(
            topicId = topicId,
            html = api.topicHtml(topicId)
                .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic),
        ) ?: return null
        val replies = firstPage.replies.toMutableList()
        // 回复超过一页时按页顺序拉取剩余页并拼接
        if (firstPage.pageCount > 1) {
            for (page in 2..firstPage.pageCount) {
                val nextPage = runCatching {
                    parser.parseTopicHtml(
                        topicId = topicId,
                        html = api.topicHtml(topicId, page = page)
                            .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic),
                    )
                }.getOrElse { error ->
                    if (error is CancellationException || error.isAccessDenied()) throw error
                    null
                } ?: continue
                replies += nextPage.replies
            }
        }
        return TopicDetail(
            topic = firstPage.toTopic(replyCount = replies.size),
            content = "",
            contentRendered = firstPage.contentRendered,
            replies = replies.withReferencePreviews(),
            viewCount = firstPage.viewCount,
            hotReplyCount = firstPage.hotReplyCount,
            tags = firstPage.tags,
        )
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
        )
    }

    private fun V2exHtmlParser.ParsedTopicHtml.toTopic(replyCount: Int): Topic = Topic(
        id = id,
        title = title,
        url = "https://www.v2ex.com/t/$id",
        node = Node(name = nodeName, title = nodeTitle),
        author = User(username = authorName, avatarUrl = authorAvatarUrl),
        avatarUrl = authorAvatarUrl,
        replyCount = replyCount,
        createdAtEpochSeconds = createdAtEpochSeconds,
    )

    private companion object {
        const val HOME_TOPICS_PAGE = 1
    }
}
