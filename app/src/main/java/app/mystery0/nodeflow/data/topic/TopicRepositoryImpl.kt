package app.mystery0.nodeflow.data.topic

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.common.isAccessDenied
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
            // 本地不缓存回复，缓存详情只对没有回复的主题是完整的；
            // 不完整的缓存不能当作成功结果，否则会出现“正文正常、回复丢失”的降级被静默吞掉
            val usableCachedDetail = localDataSource.topicDetail(topicId)
                ?.takeIf { it.replies.isNotEmpty() || it.topic.replyCount == 0 }
            runCatching { remoteDataSource.topicDetail(topicId) }
                .onSuccess { localDataSource.cacheTopicDetail(it) }
                .getOrElse { error ->
                    if (error.isAccessDenied()) {
                        localDataSource.clearTopicDetail(topicId)
                        throw error
                    }
                    usableCachedDetail ?: throw error
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
