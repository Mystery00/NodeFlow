package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.data.common.V2exReplyDto
import app.mystery0.nodeflow.data.common.V2exTopicDto
import app.mystery0.nodeflow.data.common.toReply
import app.mystery0.nodeflow.data.common.toTopic
import javax.inject.Inject
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class TopicRemoteDataSource @Inject constructor(
    private val api: V2exRawApi,
    private val json: Json,
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
        TopicDetail(
            topic = topic.toTopic(),
            content = topic.content.orEmpty(),
            contentRendered = topic.contentRendered ?: topic.content.orEmpty(),
            replies = replies,
        )
    }
}
