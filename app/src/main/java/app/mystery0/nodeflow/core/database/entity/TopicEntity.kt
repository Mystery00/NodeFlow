package app.mystery0.nodeflow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User

@Entity(tableName = "topics")
data class TopicEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val url: String,
    val nodeName: String,
    val nodeTitle: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val replyCount: Int,
    val createdAtEpochSeconds: Long?,
    val lastTouchedAtEpochSeconds: Long?,
    val lastReplyBy: String?,
    val isPinned: Boolean,
    val votes: Int,
    val content: String?,
    val contentRendered: String?,
    val cachedAtEpochMillis: Long,
)

fun TopicEntity.toTopic(): Topic {
    val author = User(username = authorName, avatarUrl = authorAvatarUrl)
    return Topic(
        id = id,
        title = title,
        url = url,
        node = Node(name = nodeName, title = nodeTitle),
        author = author,
        avatarUrl = authorAvatarUrl,
        replyCount = replyCount,
        createdAtEpochSeconds = createdAtEpochSeconds,
        lastTouchedAtEpochSeconds = lastTouchedAtEpochSeconds,
        lastReplyBy = lastReplyBy,
        isPinned = isPinned,
        votes = votes,
    )
}

fun Topic.toEntity(
    content: String? = null,
    contentRendered: String? = null,
    cachedAtEpochMillis: Long = System.currentTimeMillis(),
): TopicEntity = TopicEntity(
    id = id,
    title = title,
    url = url,
    nodeName = node.name,
    nodeTitle = node.title,
    authorName = author.username,
    authorAvatarUrl = avatarUrl ?: author.avatarUrl,
    replyCount = replyCount,
    createdAtEpochSeconds = createdAtEpochSeconds,
    lastTouchedAtEpochSeconds = lastTouchedAtEpochSeconds,
    lastReplyBy = lastReplyBy,
    isPinned = isPinned,
    votes = votes,
    content = content,
    contentRendered = contentRendered,
    cachedAtEpochMillis = cachedAtEpochMillis,
)
