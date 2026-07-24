package app.mystery0.nodeflow.data.topic

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.domain.topic.TopicDetailPager
import app.mystery0.nodeflow.domain.topic.TopicRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class TopicRepositoryImpl(
    private val remoteDataSource: TopicRemoteDataSource,
    private val localDataSource: TopicLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : TopicRepository {
    private val topicAccessStates = ConcurrentHashMap<Long, TopicAccessState>()

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

    override fun topicDetailPager(topicId: Long): TopicDetailPager = TopicDetailPagerImpl(
        topicId = topicId,
        fetchPage = { page, floorOffset ->
            remoteDataSource.topicDetailPage(topicId, page, floorOffset)
        },
        fetchJsonFallback = { remoteDataSource.jsonTopicDetailFallback(topicId) },
        localDataSource = localDataSource,
        ioDispatcher = ioDispatcher,
        accessState = topicAccessStates.computeIfAbsent(topicId) { TopicAccessState() },
    )

    override suspend fun setFavorite(
        topicId: Long,
        favorite: Boolean,
        once: String,
    ): Result<TopicDetail?> = withContext(ioDispatcher) {
        runCatching {
            val parsed = remoteDataSource.setFavorite(topicId, favorite, once)
            parsed?.let { p ->
                TopicDetail(
                    topic = p.toTopic(replyCount = p.replyCount ?: 0),
                    content = "",
                    contentRendered = p.contentRendered,
                    replies = emptyList(),
                    viewCount = p.viewCount,
                    hotReplyCount = p.hotReplyCount,
                    tags = p.tags,
                    isFavorited = p.isFavorited,
                    favoriteOnce = p.favoriteOnce,
                )
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

/** 同一主题的所有请求方共享的 access-denied 世代状态。 */
internal class TopicAccessState {
    val mutex = Mutex()
    var generation: Long = 0
    var accessDeniedError: Throwable? = null
}
