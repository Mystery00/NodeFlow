package app.mystery0.nodeflow.testing

import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.entity.TopicEntity

/** 测试用内存 TopicDao 替身。 */
open class FakeTopicDao(
    initial: List<TopicEntity> = emptyList(),
    private val clearTopicDetailFailure: Throwable? = null,
) : TopicDao {
    constructor(initial: TopicEntity) : this(listOf(initial))

    val topics = linkedMapOf<Long, TopicEntity>().apply {
        initial.forEach { put(it.id, it) }
    }

    override suspend fun topic(id: Long): TopicEntity? = topics[id]

    override suspend fun upsertTopic(topic: TopicEntity) {
        topics[topic.id] = topic
    }

    override suspend fun clearTopicDetail(topicId: Long) {
        clearTopicDetailFailure?.let { throw it }
        val current = topics[topicId] ?: return
        topics[topicId] = current.copy(content = null, contentRendered = null)
    }

    override suspend fun clear() {
        topics.clear()
    }
}
