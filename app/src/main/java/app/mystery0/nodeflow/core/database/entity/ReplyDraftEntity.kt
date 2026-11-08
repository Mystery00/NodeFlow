package app.mystery0.nodeflow.core.database.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

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
