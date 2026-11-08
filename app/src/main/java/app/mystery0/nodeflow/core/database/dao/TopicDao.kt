package app.mystery0.nodeflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import androidx.room.Transaction
import app.mystery0.nodeflow.core.database.entity.TopicEntity

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun topic(id: Long): TopicEntity?

    @Transaction
    suspend fun upsertTopics(topics: List<TopicEntity>) {
        topics.forEach { summary ->
            val cached = topic(summary.id)
            upsertTopic(summary.copy(
                content = summary.content ?: cached?.content,
                contentRendered = summary.contentRendered ?: cached?.contentRendered,
                createdAtEpochSeconds = summary.createdAtEpochSeconds ?: cached?.createdAtEpochSeconds,
            ))
        }
    }

    @Upsert
    suspend fun upsertTopic(topic: TopicEntity)

    @Query("UPDATE topics SET content = NULL, contentRendered = NULL WHERE id = :topicId")
    suspend fun clearTopicDetail(topicId: Long)

    @Query("DELETE FROM topics")
    suspend fun clear()
}
