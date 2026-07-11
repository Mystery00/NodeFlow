package app.mystery0.nodeflow.data.node

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.data.topic.HomeTopicsPagingSource
import app.mystery0.nodeflow.data.topic.TopicLocalDataSource
import app.mystery0.nodeflow.domain.node.NodeRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NodeRepositoryImpl(
    private val remoteDataSource: NodeRemoteDataSource,
    private val localDataSource: NodeLocalDataSource,
    private val topicLocalDataSource: TopicLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : NodeRepository {
    override suspend fun nodePlanes(forceRefresh: Boolean): Result<List<NodePlane>> =
        withContext(ioDispatcher) {
            runCatching {
                val cached = localDataSource.nodePlanes()
                if (!forceRefresh && cached.isNotEmpty()) return@runCatching cached
                runCatching { remoteDataSource.planes() }
                    .onSuccess { planes -> localDataSource.cacheNodePlanes(planes) }
                    .getOrElse { error ->
                        if (cached.isNotEmpty()) cached else throw error
                    }
            }
        }

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

    override fun topicsPaging(name: String): Flow<PagingData<Topic>> =
        Pager(
            config = PagingConfig(
                pageSize = NODE_TOPICS_PAGE_SIZE,
                initialLoadSize = NODE_TOPICS_PAGE_SIZE,
                prefetchDistance = NODE_TOPICS_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                HomeTopicsPagingSource(
                    loadTopics = { page ->
                        withContext(ioDispatcher) {
                            remoteDataSource.topics(name, page)
                        }
                    },
                    cacheTopics = { topics ->
                        withContext(ioDispatcher) {
                            topicLocalDataSource.cacheTopics(topics)
                        }
                    },
                )
            },
        ).flow

    override suspend fun clearCache() {
        withContext(ioDispatcher) {
            localDataSource.clear()
        }
    }

    private companion object {
        const val NODE_TOPICS_PAGE_SIZE = 20
        const val NODE_TOPICS_PREFETCH_DISTANCE = 6
    }
}
