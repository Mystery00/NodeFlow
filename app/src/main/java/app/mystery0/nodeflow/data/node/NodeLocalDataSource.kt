package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.database.entity.toNode
import app.mystery0.nodeflow.core.database.entity.toNodePlane
import app.mystery0.nodeflow.core.database.entity.toNodePlaneCache
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane

class NodeLocalDataSource(
    private val nodeDao: NodeDao,
) {
    suspend fun node(name: String): Node? = nodeDao.node(name)?.toNode()

    suspend fun nodePlanes(): List<NodePlane> =
        nodeDao.nodePlanes().map { plane ->
            plane.toNodePlane(
                nodes = nodeDao.nodesInPlane(plane.name).map { node -> node.toNode() },
            )
        }

    suspend fun cacheNode(node: Node) {
        nodeDao.upsertNode(node.toEntity())
    }

    suspend fun cacheNodes(nodes: List<Node>) {
        nodeDao.upsertNodes(nodes.map { it.toEntity() })
    }

    suspend fun cacheNodePlanes(planes: List<NodePlane>) {
        val cache = planes.toNodePlaneCache()
        nodeDao.replaceNodePlanes(
            planes = cache.planeEntities,
            nodes = cache.nodeEntities,
            links = cache.nodeLinkEntities,
        )
    }

    suspend fun clear() {
        nodeDao.clearAllNodeCache()
    }
}
