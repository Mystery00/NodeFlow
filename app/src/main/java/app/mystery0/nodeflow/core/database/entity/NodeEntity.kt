package app.mystery0.nodeflow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.mystery0.nodeflow.core.model.Node

@Entity(tableName = "nodes")
data class NodeEntity(
    @PrimaryKey val name: String,
    val id: Long?,
    val title: String,
    val header: String?,
    val avatarUrl: String?,
    val topics: Int?,
    val stars: Int?,
    val cachedAtEpochMillis: Long,
)

fun NodeEntity.toNode(): Node = Node(
    id = id,
    name = name,
    title = title,
    header = header,
    avatarUrl = avatarUrl,
    topics = topics,
    stars = stars,
)

fun Node.toEntity(cachedAtEpochMillis: Long = System.currentTimeMillis()): NodeEntity = NodeEntity(
    name = name,
    id = id,
    title = title,
    header = header,
    avatarUrl = avatarUrl,
    topics = topics,
    stars = stars,
    cachedAtEpochMillis = cachedAtEpochMillis,
)
