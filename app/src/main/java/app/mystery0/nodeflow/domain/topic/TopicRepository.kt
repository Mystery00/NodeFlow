package app.mystery0.nodeflow.domain.topic

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.ThankResult
import kotlinx.coroutines.flow.Flow

interface TopicRepository {
    suspend fun latestTopics(forceRefresh: Boolean = false): Result<List<Topic>>
    fun latestTopicsPaging(): Flow<PagingData<Topic>>
    fun topicDetailPager(topicId: Long): TopicDetailPager
    suspend fun setFavorite(topicId: Long, favorite: Boolean, once: String): Result<TopicDetail?> =
        Result.success(null)
    suspend fun thankTopic(topicId: Long, once: String): Result<ThankResult> =
        Result.failure(UnsupportedOperationException())
    suspend fun thankReply(topicId: Long, replyId: Long, once: String): Result<ThankResult> =
        Result.failure(UnsupportedOperationException())
    suspend fun clearCache()
}
