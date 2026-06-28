package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.common.IoDispatcher
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.data.topic.TopicLocalDataSource
import app.mystery0.nodeflow.domain.node.NodeRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class NodeRepositoryImpl @Inject constructor(
    private val remoteDataSource: NodeRemoteDataSource,
    private val localDataSource: NodeLocalDataSource,
    private val topicLocalDataSource: TopicLocalDataSource,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : NodeRepository {
    override suspend fun node(name: String, forceRefresh: Boolean): Result<Node> =
        withContext(ioDispatcher) {
            runCatching {
                val cached = localDataSource.node(name)
                if (!forceRefresh && cached != null) return@runCatching cached
                runCatching { remoteDataSource.node(name) }
                    .onSuccess { localDataSource.cacheNode(it) }
                    .getOrElse { error -> cached ?: throw error }
            }
        }

    override suspend fun topics(
        name: String,
        page: Int,
        forceRefresh: Boolean,
    ): Result<List<Topic>> = withContext(ioDispatcher) {
        runCatching {
            val cached = topicLocalDataSource.nodeTopics(name)
            if (!forceRefresh && page == 1 && cached.isNotEmpty()) return@runCatching cached
            runCatching { remoteDataSource.topics(name, page) }
                .onSuccess { topicLocalDataSource.cacheTopics(it) }
                .getOrElse { error ->
                    if (cached.isNotEmpty()) cached else throw error
                }
        }
    }

    override suspend fun clearCache() {
        withContext(ioDispatcher) {
            localDataSource.clear()
        }
    }
}
