package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.entity.NodeEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneNodeEntity
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NodeLocalDataSourceTest {
    @Test
    fun cacheNodePlanes_restoresPlanesAndNodesInOrder() = runTest {
        val dataSource = NodeLocalDataSource(FakeNodeDao())
        val planes = listOf(
            NodePlane(
                name = "Limbo",
                title = "混沌海",
                nodes = listOf(
                    Node(name = "earth", title = "地球"),
                    Node(name = "qna", title = "问与答"),
                ),
            ),
            NodePlane(
                name = "Mechanus",
                title = "机械境",
                nodes = listOf(Node(name = "android", title = "Android")),
            ),
        )

        dataSource.cacheNodePlanes(planes)

        val cached = dataSource.nodePlanes()
        assertThat(cached.map { it.name }).containsExactly("Limbo", "Mechanus").inOrder()
        assertThat(cached.first().nodes.map { it.name }).containsExactly("earth", "qna").inOrder()
        assertThat(cached[1].nodes.map { it.name }).containsExactly("android").inOrder()
    }

    @Test
    fun cacheNodePlanes_replacesPreviousPlaneLinks() = runTest {
        val dataSource = NodeLocalDataSource(FakeNodeDao())
        dataSource.cacheNodePlanes(
            listOf(
                NodePlane(
                    name = "Limbo",
                    title = "混沌海",
                    nodes = listOf(Node(name = "earth", title = "地球")),
                ),
            ),
        )

        dataSource.cacheNodePlanes(
            listOf(
                NodePlane(
                    name = "Mechanus",
                    title = "机械境",
                    nodes = listOf(Node(name = "android", title = "Android")),
                ),
            ),
        )

        val cached = dataSource.nodePlanes()
        assertThat(cached.map { it.name }).containsExactly("Mechanus")
        assertThat(cached.single().nodes.map { it.name }).containsExactly("android")
    }
}

private class FakeNodeDao : NodeDao {
    private val nodes = linkedMapOf<String, NodeEntity>()
    private val planes = linkedMapOf<String, NodePlaneEntity>()
    private val links = mutableListOf<NodePlaneNodeEntity>()

    override suspend fun node(name: String): NodeEntity? = nodes[name]

    override suspend fun nodePlanes(): List<NodePlaneEntity> =
        planes.values.sortedBy { it.sortOrder }

    override suspend fun nodesInPlane(planeName: String): List<NodeEntity> =
        links
            .filter { it.planeName == planeName }
            .sortedBy { it.sortOrder }
            .mapNotNull { nodes[it.nodeName] }

    override suspend fun upsertNode(node: NodeEntity) {
        nodes[node.name] = node
    }

    override suspend fun upsertNodes(nodes: List<NodeEntity>) {
        nodes.forEach { node -> this.nodes[node.name] = node }
    }

    override suspend fun upsertNodePlanes(planes: List<NodePlaneEntity>) {
        planes.forEach { plane -> this.planes[plane.name] = plane }
    }

    override suspend fun insertNodePlaneNodes(nodes: List<NodePlaneNodeEntity>) {
        nodes.forEach { node ->
            links.removeAll { it.planeName == node.planeName && it.nodeName == node.nodeName }
            links += node
        }
    }

    override suspend fun clearNodePlaneNodes() {
        links.clear()
    }

    override suspend fun clearNodePlanes() {
        planes.clear()
    }

    override suspend fun clear() {
        nodes.clear()
    }

    override suspend fun replaceNodePlanes(
        planes: List<NodePlaneEntity>,
        nodes: List<NodeEntity>,
        links: List<NodePlaneNodeEntity>,
    ) {
        clearNodePlaneNodes()
        clearNodePlanes()
        upsertNodes(nodes)
        upsertNodePlanes(planes)
        insertNodePlaneNodes(links)
    }

    override suspend fun clearAllNodeCache() {
        clearNodePlaneNodes()
        clearNodePlanes()
        clear()
    }
}
