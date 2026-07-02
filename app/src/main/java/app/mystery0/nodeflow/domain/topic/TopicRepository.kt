package app.mystery0.nodeflow.domain.topic

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import kotlinx.coroutines.flow.Flow

interface TopicRepository {
    suspend fun latestTopics(forceRefresh: Boolean = false): Result<List<Topic>>
    fun latestTopicsPaging(): Flow<PagingData<Topic>>
    suspend fun topicDetail(topicId: Long, forceRefresh: Boolean = false): Result<TopicDetail>
    suspend fun clearCache()
}
