package app.mystery0.nodeflow.core.database.entity

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodePlaneEntityTest {
    @Test
    fun toNodePlaneCache_preservesPlaneAndNodeOrder() {
        val planes = listOf(
            NodePlane(
                name = "Limbo",
                title = "混沌海",
                nodeCount = 2,
                avatarUrl = "https://example.com/limbo.png",
                nodes = listOf(
                    Node(name = "earth", title = "地球"),
                    Node(name = "qna", title = "问与答"),
                ),
            ),
            NodePlane(
                name = "Mechanus",
                title = "机械境",
                nodeCount = 1,
                nodes = listOf(Node(name = "android", title = "Android")),
            ),
        )

        val cache = planes.toNodePlaneCache(cachedAtEpochMillis = 1234L)

        assertThat(cache.planeEntities.map { it.name }).containsExactly("Limbo", "Mechanus").inOrder()
        assertThat(cache.planeEntities.map { it.sortOrder }).containsExactly(0, 1).inOrder()
        assertThat(cache.nodeLinkEntities.map { it.planeName to it.nodeName }).containsExactly(
            "Limbo" to "earth",
            "Limbo" to "qna",
            "Mechanus" to "android",
        ).inOrder()
        assertThat(cache.nodeLinkEntities.map { it.sortOrder }).containsExactly(0, 1, 0).inOrder()
        assertThat(cache.nodeEntities.map { it.name }).containsExactly("earth", "qna", "android").inOrder()
    }

    @Test
    fun cachedNodePlane_toNodePlane_restoresNodes() {
        val plane = NodePlaneEntity(
            name = "Limbo",
            title = "混沌海",
            nodeCount = 2,
            avatarUrl = "https://example.com/limbo.png",
            sortOrder = 0,
            cachedAtEpochMillis = 1234L,
        )
        val nodes = listOf(
            Node(name = "earth", title = "地球"),
            Node(name = "qna", title = "问与答"),
        )

        val restored = plane.toNodePlane(nodes)

        assertThat(restored.name).isEqualTo("Limbo")
        assertThat(restored.title).isEqualTo("混沌海")
        assertThat(restored.nodeCount).isEqualTo(2)
        assertThat(restored.avatarUrl).isEqualTo("https://example.com/limbo.png")
        assertThat(restored.nodes.map { it.name }).containsExactly("earth", "qna").inOrder()
    }
}
