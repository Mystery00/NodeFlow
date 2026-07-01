package app.mystery0.nodeflow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane

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

@Entity(tableName = "node_planes")
data class NodePlaneEntity(
    @PrimaryKey val name: String,
    val title: String,
    val nodeCount: Int?,
    val avatarUrl: String?,
    val sortOrder: Int,
    val cachedAtEpochMillis: Long,
)

@Entity(
    tableName = "node_plane_nodes",
    primaryKeys = ["planeName", "nodeName"],
    foreignKeys = [
        ForeignKey(
            entity = NodePlaneEntity::class,
            parentColumns = ["name"],
            childColumns = ["planeName"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NodeEntity::class,
            parentColumns = ["name"],
            childColumns = ["nodeName"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("planeName"),
        Index("nodeName"),
    ],
)
data class NodePlaneNodeEntity(
    val planeName: String,
    val nodeName: String,
    val sortOrder: Int,
    val cachedAtEpochMillis: Long,
)

data class NodePlaneCache(
    val planeEntities: List<NodePlaneEntity>,
    val nodeEntities: List<NodeEntity>,
    val nodeLinkEntities: List<NodePlaneNodeEntity>,
)

fun List<NodePlane>.toNodePlaneCache(
    cachedAtEpochMillis: Long = System.currentTimeMillis(),
): NodePlaneCache {
    val planeEntities = mapIndexed { planeIndex, plane ->
        NodePlaneEntity(
            name = plane.name,
            title = plane.title,
            nodeCount = plane.nodeCount,
            avatarUrl = plane.avatarUrl,
            sortOrder = planeIndex,
            cachedAtEpochMillis = cachedAtEpochMillis,
        )
    }
    val nodeEntities = flatMap { plane -> plane.nodes }
        .distinctBy { node -> node.name }
        .map { node -> node.toEntity(cachedAtEpochMillis) }
    val nodeLinkEntities = flatMap { plane ->
        plane.nodes.mapIndexed { nodeIndex, node ->
            NodePlaneNodeEntity(
                planeName = plane.name,
                nodeName = node.name,
                sortOrder = nodeIndex,
                cachedAtEpochMillis = cachedAtEpochMillis,
            )
        }
    }
    return NodePlaneCache(
        planeEntities = planeEntities,
        nodeEntities = nodeEntities,
        nodeLinkEntities = nodeLinkEntities,
    )
}

fun NodePlaneEntity.toNodePlane(nodes: List<Node>): NodePlane = NodePlane(
    name = name,
    title = title,
    nodeCount = nodeCount,
    avatarUrl = avatarUrl,
    nodes = nodes,
)
