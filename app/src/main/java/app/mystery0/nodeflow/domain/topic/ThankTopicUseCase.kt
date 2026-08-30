package app.mystery0.nodeflow.domain.topic

class ThankTopicUseCase(private val repository: TopicRepository) {
    suspend operator fun invoke(topicId: Long, once: String) = repository.thankTopic(topicId, once)
}
