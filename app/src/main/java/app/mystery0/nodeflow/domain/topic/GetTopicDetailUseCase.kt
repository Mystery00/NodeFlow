package app.mystery0.nodeflow.domain.topic

class GetTopicDetailUseCase(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: Long, forceRefresh: Boolean = false) =
        repository.topicDetail(topicId, forceRefresh)
}
