package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.database.entity.toTopic
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail

class TopicLocalDataSource(
    private val topicDao: TopicDao,
) {
    suspend fun latestTopics(): List<Topic> =
        topicDao.latestTopics().map { it.toTopic() }

    suspend fun nodeTopics(nodeName: String): List<Topic> =
        topicDao.topicsByNode(nodeName).map { it.toTopic() }

    suspend fun topic(topicId: Long): Topic? =
        topicDao.topic(topicId)?.toTopic()

    suspend fun topicDetail(topicId: Long): TopicDetail? =
        topicDao.topic(topicId)?.let { entity ->
            TopicDetail(
                topic = entity.toTopic(),
                content = entity.content.orEmpty(),
                contentRendered = entity.contentRendered.orEmpty(),
                replies = emptyList(),
            )
        }

    suspend fun cacheTopics(topics: List<Topic>) {
        topicDao.upsertTopics(topics.map { it.toEntity() })
    }

    suspend fun cacheTopicDetail(detail: TopicDetail) {
        topicDao.upsertTopic(
            detail.topic.toEntity(
                content = detail.content,
                contentRendered = detail.contentRendered,
            ),
        )
    }

    suspend fun clear() {
        topicDao.clear()
    }
}
