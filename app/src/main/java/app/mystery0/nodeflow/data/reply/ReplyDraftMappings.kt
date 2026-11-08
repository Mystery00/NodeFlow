package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.database.entity.ReplyDraftEntity
import app.mystery0.nodeflow.core.database.entity.ReplyDraftImageEntity
import app.mystery0.nodeflow.core.database.entity.ReplyDraftWithImages
import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage

fun ReplyDraftWithImages.toDomain() = ReplyDraft(
    topicId = draft.topicId,
    content = draft.content,
    selectionStart = draft.selectionStart,
    selectionEnd = draft.selectionEnd,
    images = images.map(ReplyDraftImageEntity::toDomain),
    updatedAtEpochMillis = draft.updatedAtEpochMillis,
)

fun ReplyDraft.toEntity() = ReplyDraftEntity(
    topicId = topicId,
    content = content,
    selectionStart = selectionStart,
    selectionEnd = selectionEnd,
    updatedAtEpochMillis = updatedAtEpochMillis,
)

fun UploadedReplyImage.toEntity(topicId: Long) = ReplyDraftImageEntity(
    imageId = imageId,
    topicId = topicId,
    originalUrl = originalUrl,
    detailUrl = detailUrl,
    originalFileName = originalFileName,
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun ReplyDraftImageEntity.toDomain() = UploadedReplyImage(
    imageId = imageId,
    originalUrl = originalUrl,
    detailUrl = detailUrl,
    originalFileName = originalFileName,
    createdAtEpochMillis = createdAtEpochMillis,
)
