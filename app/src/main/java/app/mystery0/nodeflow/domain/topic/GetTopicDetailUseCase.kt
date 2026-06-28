package app.mystery0.nodeflow.domain.topic

import javax.inject.Inject

class GetTopicDetailUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: Long, forceRefresh: Boolean = false) =
        repository.topicDetail(topicId, forceRefresh)
}
