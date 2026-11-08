package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.runSuspendCatching
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.domain.topic.TopicDetailPager
import app.mystery0.nodeflow.domain.topic.TopicRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.lang.ref.WeakReference

class TopicRepositoryImpl(
    private val remoteDataSource: TopicRemoteDataSource,
    private val localDataSource: TopicLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : TopicRepository {
    private val topicAccessStates = ConcurrentHashMap<Long, WeakReference<TopicAccessState>>()
    // 拒绝状态不能随内存回收而消失，否则缓存删除失败后可能重新暴露旧正文。
    private val deniedAccessStates = ConcurrentHashMap<Long, TopicAccessState>()

    private fun accessState(topicId: Long): TopicAccessState = synchronized(topicAccessStates) {
        topicAccessStates.entries.removeAll { it.value.get() == null }
        deniedAccessStates[topicId] ?: topicAccessStates[topicId]?.get()
            ?: TopicAccessState { state, denied ->
                if (denied) deniedAccessStates[topicId] = state
                else deniedAccessStates.remove(topicId, state)
            }.also { topicAccessStates[topicId] = WeakReference(it) }
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
        accessState = accessState(topicId),
    )

    override suspend fun setFavorite(
        topicId: Long,
        favorite: Boolean,
        once: String,
    ): Result<TopicDetail?> = withContext(ioDispatcher) {
        runSuspendCatching {
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
                    isThanked = p.isThanked,
                    thankOnce = p.thankOnce,
                    appends = p.appends,
                )
            }
        }
    }

    override suspend fun thankTopic(topicId: Long, once: String) = withContext(ioDispatcher) {
        runSuspendCatching { remoteDataSource.thankTopic(topicId, once) }
    }

    override suspend fun thankReply(topicId: Long, replyId: Long, once: String) = withContext(ioDispatcher) {
        runSuspendCatching { remoteDataSource.thankReply(topicId, replyId, once) }
    }

    override suspend fun clearCache() {
        withContext(ioDispatcher) {
            val states = synchronized(topicAccessStates) {
                (topicAccessStates.mapNotNull { (id, reference) -> reference.get()?.let { id to it } } +
                    deniedAccessStates.map { it.key to it.value })
                    .distinctBy { it.first }.sortedBy { it.first }.map { it.second }
            }
            val locked = mutableListOf<TopicAccessState>()
            try {
                // 多次清理按同一主题顺序取锁，避免交叉等待。
                states.forEach { state -> state.mutex.lock(); locked += state }
                localDataSource.clear()
                states.forEach { state ->
                    state.generation += 1
                    state.accessDeniedError = null
                }
            } finally {
                locked.asReversed().forEach { it.mutex.unlock() }
            }
        }
    }

    private companion object {
        const val HOME_TOPICS_PAGE_SIZE = 20
        const val HOME_TOPICS_PREFETCH_DISTANCE = 6
    }
}

/** 同一主题的所有请求方共享的 access-denied 世代状态。 */
internal class TopicAccessState(
    private val onDenialChanged: (TopicAccessState, Boolean) -> Unit = { _, _ -> },
) {
    val mutex = Mutex()
    var generation: Long = 0
    var accessDeniedError: Throwable? = null
        set(value) {
            field = value
            onDenialChanged(this, value != null)
        }
}
