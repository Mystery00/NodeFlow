package app.mystery0.nodeflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.mystery0.nodeflow.core.database.entity.NodeEntity

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes WHERE name = :name")
    suspend fun node(name: String): NodeEntity?

    @Upsert
    suspend fun upsertNode(node: NodeEntity)

    @Query("DELETE FROM nodes")
    suspend fun clear()
}
