package app.mystery0.nodeflow.domain.node

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import kotlinx.coroutines.flow.Flow

interface NodeRepository {
    suspend fun nodePlanes(forceRefresh: Boolean = false): Result<List<NodePlane>>
    suspend fun node(name: String, forceRefresh: Boolean = false): Result<Node>
    suspend fun topics(name: String, page: Int = 1, forceRefresh: Boolean = false): Result<List<Topic>>
    fun topicsPaging(name: String): Flow<PagingData<Topic>>
    suspend fun blockNode(name: String): Result<Unit>
    suspend fun clearCache()
}
