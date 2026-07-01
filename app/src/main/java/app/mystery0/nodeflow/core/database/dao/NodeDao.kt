package app.mystery0.nodeflow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import app.mystery0.nodeflow.core.database.entity.NodeEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneNodeEntity

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes WHERE name = :name")
    suspend fun node(name: String): NodeEntity?

    @Query("SELECT * FROM node_planes ORDER BY sortOrder ASC")
    suspend fun nodePlanes(): List<NodePlaneEntity>

    @Query(
        """
        SELECT nodes.* FROM nodes
        INNER JOIN node_plane_nodes ON nodes.name = node_plane_nodes.nodeName
        WHERE node_plane_nodes.planeName = :planeName
        ORDER BY node_plane_nodes.sortOrder ASC
        """,
    )
    suspend fun nodesInPlane(planeName: String): List<NodeEntity>

    @Upsert
    suspend fun upsertNode(node: NodeEntity)

    @Upsert
    suspend fun upsertNodes(nodes: List<NodeEntity>)

    @Upsert
    suspend fun upsertNodePlanes(planes: List<NodePlaneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNodePlaneNodes(nodes: List<NodePlaneNodeEntity>)

    @Query("DELETE FROM node_plane_nodes")
    suspend fun clearNodePlaneNodes()

    @Query("DELETE FROM node_planes")
    suspend fun clearNodePlanes()

    @Transaction
    suspend fun replaceNodePlanes(
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

    @Query("DELETE FROM nodes")
    suspend fun clear()

    @Transaction
    suspend fun clearAllNodeCache() {
        clearNodePlaneNodes()
        clearNodePlanes()
        clear()
    }
}
