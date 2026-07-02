package app.mystery0.nodeflow.domain.topic

class GetLatestTopicsPagingUseCase(
    private val repository: TopicRepository,
) {
    operator fun invoke() = repository.latestTopicsPaging()
}
