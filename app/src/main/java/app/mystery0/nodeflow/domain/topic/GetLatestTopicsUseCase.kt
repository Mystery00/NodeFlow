package app.mystery0.nodeflow.domain.topic

import javax.inject.Inject

class GetLatestTopicsUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(forceRefresh: Boolean = false) =
        repository.latestTopics(forceRefresh)
}
