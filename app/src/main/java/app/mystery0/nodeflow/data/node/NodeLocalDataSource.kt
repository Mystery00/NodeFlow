package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.database.entity.toNode
import app.mystery0.nodeflow.core.model.Node
import javax.inject.Inject

class NodeLocalDataSource @Inject constructor(
    private val nodeDao: NodeDao,
) {
    suspend fun node(name: String): Node? = nodeDao.node(name)?.toNode()

    suspend fun cacheNode(node: Node) {
        nodeDao.upsertNode(node.toEntity())
    }

    suspend fun clear() {
        nodeDao.clear()
    }
}
