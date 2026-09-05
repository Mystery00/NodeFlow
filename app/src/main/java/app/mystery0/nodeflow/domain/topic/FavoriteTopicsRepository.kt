package app.mystery0.nodeflow.domain.topic

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.Topic
import kotlinx.coroutines.flow.Flow

interface FavoriteTopicsRepository {
    fun favoriteTopicsPaging(): Flow<PagingData<Topic>>
}
