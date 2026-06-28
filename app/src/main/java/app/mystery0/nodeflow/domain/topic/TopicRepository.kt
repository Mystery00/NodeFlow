package app.mystery0.nodeflow.domain.topic

import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail

interface TopicRepository {
    suspend fun latestTopics(forceRefresh: Boolean = false): Result<List<Topic>>
    suspend fun topicDetail(topicId: Long, forceRefresh: Boolean = false): Result<TopicDetail>
    suspend fun clearCache()
}
