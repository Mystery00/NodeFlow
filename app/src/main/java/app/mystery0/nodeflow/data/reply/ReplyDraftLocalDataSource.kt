package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.database.dao.ReplyDraftDao
import app.mystery0.nodeflow.core.database.entity.toDomain
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage

class ReplyDraftLocalDataSource(private val dao: ReplyDraftDao) {
    suspend fun load(topicId: Long): ReplyDraft? = dao.draft(topicId)?.toDomain()
    suspend fun save(draft: ReplyDraft) = dao.upsertDraft(draft.toEntity())
    suspend fun addImage(topicId: Long, image: UploadedReplyImage) =
        dao.upsertImage(image.toEntity(topicId))
    suspend fun clear(topicId: Long) = dao.delete(topicId)
}
