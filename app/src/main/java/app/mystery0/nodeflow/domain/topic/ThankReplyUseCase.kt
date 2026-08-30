package app.mystery0.nodeflow.domain.topic

class ThankReplyUseCase(private val repository: TopicRepository) {
    suspend operator fun invoke(topicId: Long, replyId: Long, once: String) =
        repository.thankReply(topicId, replyId, once)
}
