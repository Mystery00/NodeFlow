package app.mystery0.nodeflow.domain.topic

class GetLatestTopicsUseCase(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false) =
        repository.latestTopics(forceRefresh)
}
