package app.mystery0.nodeflow.data.topic

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.domain.topic.FavoriteTopicsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class FavoriteTopicsRepositoryImpl(
    private val remoteDataSource: TopicRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : FavoriteTopicsRepository {
    override fun favoriteTopicsPaging(): Flow<PagingData<Topic>> = Pager(
        config = PagingConfig(pageSize = 20, initialLoadSize = 20, prefetchDistance = 6, enablePlaceholders = false),
        pagingSourceFactory = {
            FavoriteTopicsPagingSource { page ->
                withContext(ioDispatcher) { remoteDataSource.favoriteTopics(page) }
            }
        },
    ).flow
}
