package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.data.common.V2exReplyDto
import app.mystery0.nodeflow.data.common.V2exTopicDto
import app.mystery0.nodeflow.data.common.toReply
import app.mystery0.nodeflow.data.common.toTopic
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class TopicRemoteDataSource(
    private val api: V2exRawApi,
    private val json: Json,
    private val parser: V2exHtmlParser,
) {
    suspend fun latestTopics(): List<Topic> = safeNetworkCall {
        json.decodeFromString<List<V2exTopicDto>>(api.latestTopics().bodyStringOrThrow())
            .map { it.toTopic() }
    }

    suspend fun topicDetail(topicId: Long): TopicDetail = safeNetworkCall {
        val topic = json.decodeFromString<List<V2exTopicDto>>(api.topic(topicId).bodyStringOrThrow())
            .first()
        val replies = json.decodeFromString<List<V2exReplyDto>>(api.replies(topicId).bodyStringOrThrow())
            .mapIndexed { index, dto -> dto.toReply(topicIdFallback = topicId, floor = index + 1) }
            .withReferencePreviews()
        val supplemental = runCatching {
            parser.parseTopicHtml(
                topicId = topicId,
                html = api.topicHtml(topicId).bodyStringOrThrow(),
            )
        }.getOrNull()
        TopicDetail(
            topic = topic.toTopic(),
            content = topic.content.orEmpty(),
            contentRendered = topic.contentRendered ?: topic.content.orEmpty(),
            replies = replies,
            viewCount = supplemental?.viewCount,
            hotReplyCount = supplemental?.hotReplyCount,
            tags = supplemental?.tags.orEmpty(),
        )
    }
}
