package app.mystery0.nodeflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.mystery0.nodeflow.core.database.entity.ReplyDraftEntity
import app.mystery0.nodeflow.core.database.entity.ReplyDraftImageEntity
import app.mystery0.nodeflow.core.database.entity.ReplyDraftWithImages

@Dao
interface ReplyDraftDao {
    @Transaction
    @Query("SELECT * FROM reply_drafts WHERE topicId = :topicId")
    suspend fun draft(topicId: Long): ReplyDraftWithImages?

    @Upsert
    suspend fun upsertDraft(draft: ReplyDraftEntity)

    @Upsert
    suspend fun upsertImage(image: ReplyDraftImageEntity)

    @Query("DELETE FROM reply_drafts WHERE topicId = :topicId")
    suspend fun delete(topicId: Long)
}
