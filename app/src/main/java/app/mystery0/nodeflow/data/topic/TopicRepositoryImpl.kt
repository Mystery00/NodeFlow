package app.mystery0.nodeflow.data.topic

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.common.isAccessDenied
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.domain.topic.TopicRepository
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    override suspend fun topicDetail(
        topicId: Long,
        forceRefresh: Boolean,
    ): Result<TopicDetail> = withContext(ioDispatcher) {
        try {
            val accessState = topicAccessStates.computeIfAbsent(topicId) { TopicAccessState() }
            val requestGeneration = accessState.mutex.withLock { accessState.generation }
            // 本地不缓存回复，缓存详情只对没有回复的主题是完整的；
            // 不完整的缓存不能当作成功结果，否则会出现“正文正常、回复丢失”的降级被静默吞掉
            val usableCachedDetail = localDataSource.topicDetail(topicId)
                ?.takeIf { it.replies.isNotEmpty() || it.topic.replyCount == 0 }
            val remoteDetail = try {
                remoteDataSource.topicDetail(topicId)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                val fallback = accessState.mutex.withLock {
                    if (error.isAccessDenied()) {
                        accessState.generation += 1
                        accessState.accessDeniedError = error
                        try {
                            localDataSource.clearTopicDetail(topicId)
                        } catch (clearError: CancellationException) {
                            throw clearError
                        } catch (clearError: Throwable) {
                            if (clearError !== error) error.addSuppressed(clearError)
                        }
                        throw error
                    }
                    // 权限状态推进后，较早请求捕获的缓存不能重新成为回退结果。
                    if (accessState.generation != requestGeneration) {
                        accessState.accessDeniedError?.let { throw it }
                        throw CancellationException("Topic detail request was superseded")
                    }
                    accessState.accessDeniedError?.let { throw it }
                    usableCachedDetail ?: throw error
                }
                return@withContext Result.success(fallback)
            }
            val cachedDetail = accessState.mutex.withLock {
                // 请求发出后若出现了更新的权限拒绝，较早的成功响应不能覆盖它
                if (accessState.generation != requestGeneration) {
                    accessState.accessDeniedError?.let { throw it }
                    throw CancellationException("Topic detail request was superseded")
                }
                localDataSource.cacheTopicDetail(remoteDetail)
                accessState.accessDeniedError = null
                remoteDetail
            }
            Result.success(cachedDetail)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error)
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

    private class TopicAccessState {
        val mutex = Mutex()
        var generation: Long = 0
        var accessDeniedError: Throwable? = null
    }
}
