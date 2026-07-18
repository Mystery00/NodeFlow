package app.mystery0.nodeflow.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage

@Entity(tableName = "reply_drafts")
data class ReplyDraftEntity(
    @PrimaryKey val topicId: Long,
    val content: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "reply_draft_images",
    foreignKeys = [
        ForeignKey(
            entity = ReplyDraftEntity::class,
            parentColumns = ["topicId"],
            childColumns = ["topicId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("topicId")],
)
data class ReplyDraftImageEntity(
    @PrimaryKey val imageId: String,
    val topicId: Long,
    val originalUrl: String,
    val detailUrl: String,
    val originalFileName: String,
    val createdAtEpochMillis: Long,
)

data class ReplyDraftWithImages(
    @Embedded val draft: ReplyDraftEntity,
    @Relation(parentColumn = "topicId", entityColumn = "topicId")
    val images: List<ReplyDraftImageEntity>,
)

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
