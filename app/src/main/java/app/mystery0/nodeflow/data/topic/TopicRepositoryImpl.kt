package app.mystery0.nodeflow.data.topic

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.domain.topic.TopicRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TopicRepositoryImpl(
    private val remoteDataSource: TopicRemoteDataSource,
    private val localDataSource: TopicLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : TopicRepository {
    override suspend fun latestTopics(forceRefresh: Boolean): Result<List<Topic>> =
        withContext(ioDispatcher) {
            runCatching {
                val cached = localDataSource.latestTopics()
                if (!forceRefresh && cached.isNotEmpty()) return@runCatching cached
                runCatching { remoteDataSource.latestTopics() }
                    .onSuccess { localDataSource.cacheTopics(it) }
                    .getOrElse { error ->
                        if (cached.isNotEmpty()) cached else throw error
                }
            }
        }

    override fun latestTopicsPaging(): Flow<PagingData<Topic>> =
        Pager(
            config = PagingConfig(
                pageSize = HOME_TOPICS_PAGE_SIZE,
                initialLoadSize = HOME_TOPICS_PAGE_SIZE,
                prefetchDistance = HOME_TOPICS_PREFETCH_DISTANCE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = {
                HomeTopicsPagingSource(
                    loadTopics = { page ->
                        withContext(ioDispatcher) {
                            remoteDataSource.homeTopics(page)
                        }
                    },
                    cacheTopics = { topics ->
                        withContext(ioDispatcher) {
                            localDataSource.cacheTopics(topics)
                        }
                    },
                )
            },
        ).flow

    override suspend fun topicDetail(
        topicId: Long,
        forceRefresh: Boolean,
    ): Result<TopicDetail> = withContext(ioDispatcher) {
        runCatching {
            val cachedDetail = localDataSource.topicDetail(topicId)
            val cachedTopic = cachedDetail?.topic ?: localDataSource.topic(topicId)
            if (
                !forceRefresh &&
                cachedDetail != null &&
                cachedDetail.contentRendered.isNotBlank() &&
                cachedDetail.replies.isNotEmpty()
            ) {
                return@runCatching cachedDetail
            }
            runCatching { remoteDataSource.topicDetail(topicId) }
                .onSuccess { localDataSource.cacheTopicDetail(it) }
                .getOrElse { error ->
                    when {
                        cachedDetail != null -> cachedDetail
                        cachedTopic != null -> TopicDetail(
                            topic = cachedTopic,
                            content = "",
                            contentRendered = "",
                            replies = emptyList(),
                        )
                        else -> throw error
                    }
                }
        }
    }

    override suspend fun clearCache() {
        withContext(ioDispatcher) {
            localDataSource.clear()
        }
    }

    private companion object {
        const val HOME_TOPICS_PAGE_SIZE = 20
        const val HOME_TOPICS_PREFETCH_DISTANCE = 6
    }
}
