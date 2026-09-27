package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.database.dao.ReplyDraftDao
import app.mystery0.nodeflow.core.database.entity.toDomain
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage

class ReplyDraftLocalDataSource(private val dao: ReplyDraftDao) {
    suspend fun load(topicId: Long): ReplyDraft? = dao.draft(topicId)?.toDomain()?.normalizeImages()
    suspend fun save(draft: ReplyDraft) = dao.upsertDraft(draft.copy(images = draft.images.normalized()).toEntity())
    suspend fun addImage(topicId: Long, image: UploadedReplyImage) =
        dao.upsertImage(image.copy(imageId = ReplyImageIdCodec.normalize(image.imageId)).toEntity(topicId))

    private fun ReplyDraft.normalizeImages() = copy(images = images.normalized())

    private fun List<UploadedReplyImage>.normalized() =
        map { it.copy(imageId = ReplyImageIdCodec.normalize(it.imageId)) }
            .distinctBy { it.imageId }
    suspend fun clear(topicId: Long) = dao.delete(topicId)
}
