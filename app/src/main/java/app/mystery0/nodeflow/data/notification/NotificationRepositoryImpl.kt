package app.mystery0.nodeflow.data.notification

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.domain.notification.NotificationRepository
import app.mystery0.nodeflow.domain.topic.TopicDetailPager
import app.mystery0.nodeflow.domain.topic.TopicRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NotificationRepositoryImpl(
    private val remoteDataSource: NotificationRemoteDataSource,
    private val topicRepository: TopicRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : NotificationRepository {
    override fun notificationsPaging(): Flow<PagingData<Notification>> = Pager(
        config = PagingConfig(
            pageSize = NotificationPagingSource.PAGE_SIZE,
            initialLoadSize = NotificationPagingSource.PAGE_SIZE,
            prefetchDistance = 10,
            enablePlaceholders = false,
        ),
        pagingSourceFactory = {
            NotificationPagingSource(
                loadPage = { page ->
                    withContext(ioDispatcher) { remoteDataSource.notifications(page) }
                },
                enrichReferences = { notifications ->
                    // 同批次内同主题复用同一 pager，不同楼层靠 pager 增量补页
                    val pagers = mutableMapOf<Long, TopicDetailPager>()
                    enrichNotificationReferences(notifications) { topicId, floor ->
                        pagers.getOrPut(topicId) { topicRepository.topicDetailPager(topicId) }
                            .loadUntilFloor(floor)
                            .map { it.detail }
                    }
                },
            )
        },
    ).flow
}
