package app.mystery0.nodeflow.feature.node

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodeListContentTest {
    @Test
    fun filterNodePlanes_returnsOriginalPlanesForBlankQuery() {
        val planes = samplePlanes()

        val filtered = filterNodePlanes(planes, " ")

        assertThat(filtered).isEqualTo(planes)
    }

    @Test
    fun filterNodePlanes_keepsOnlyMatchedNodesAndDropsEmptyPlanes() {
        val filtered = filterNodePlanes(samplePlanes(), "and")

        assertThat(filtered).hasSize(1)
        assertThat(filtered.single().name).isEqualTo("Mechanus")
        assertThat(filtered.single().nodes.map { it.name }).containsExactly("android").inOrder()
    }

    @Test
    fun nodePlaneSubtitle_usesNodeCountWhenAvailable() {
        val plane = NodePlane(
            name = "Limbo",
            title = "混沌海",
            nodeCount = 110,
            nodes = listOf(Node(name = "earth", title = "地球")),
        )

        assertThat(nodePlaneSubtitle(plane)).isEqualTo("Limbo · 110 个节点")
    }

    private fun samplePlanes(): List<NodePlane> = listOf(
        NodePlane(
            name = "Limbo",
            title = "混沌海",
            nodeCount = 2,
            nodes = listOf(
                Node(name = "earth", title = "地球"),
                Node(name = "qna", title = "问与答"),
            ),
        ),
        NodePlane(
            name = "Mechanus",
            title = "机械境",
            nodeCount = 2,
            nodes = listOf(
                Node(name = "android", title = "Android"),
                Node(name = "iphone", title = "iPhone"),
            ),
        ),
    )
}
