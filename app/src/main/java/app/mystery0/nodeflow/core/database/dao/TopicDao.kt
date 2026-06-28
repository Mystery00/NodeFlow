package app.mystery0.nodeflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.mystery0.nodeflow.core.database.entity.TopicEntity

@Dao
interface TopicDao {
    @Query("SELECT * FROM topics ORDER BY COALESCE(lastTouchedAtEpochSeconds, createdAtEpochSeconds, 0) DESC LIMIT :limit")
    suspend fun latestTopics(limit: Int = 40): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE nodeName = :nodeName ORDER BY COALESCE(lastTouchedAtEpochSeconds, createdAtEpochSeconds, 0) DESC LIMIT :limit")
    suspend fun topicsByNode(nodeName: String, limit: Int = 40): List<TopicEntity>

    @Query("SELECT * FROM topics WHERE id = :id")
    suspend fun topic(id: Long): TopicEntity?

    @Upsert
    suspend fun upsertTopics(topics: List<TopicEntity>)

    @Upsert
    suspend fun upsertTopic(topic: TopicEntity)

    @Query("DELETE FROM topics")
    suspend fun clear()
}
