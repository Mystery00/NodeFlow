package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.database.entity.toNode
import app.mystery0.nodeflow.core.model.Node

class NodeLocalDataSource(
    private val nodeDao: NodeDao,
) {
    suspend fun node(name: String): Node? = nodeDao.node(name)?.toNode()

    suspend fun cacheNode(node: Node) {
        nodeDao.upsertNode(node.toEntity())
    }

    suspend fun cacheNodes(nodes: List<Node>) {
        nodeDao.upsertNodes(nodes.map { it.toEntity() })
    }

    suspend fun clear() {
        nodeDao.clear()
    }
}
