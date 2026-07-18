package app.mystery0.nodeflow.domain.reply

class GetReplyConstraintsUseCase(private val repository: ReplyRepository) {
    suspend operator fun invoke(topicId: Long) = repository.loadConstraints(topicId)
}

class CreateReplyUseCase(private val repository: ReplyRepository) {
    suspend operator fun invoke(topicId: Long, content: String, username: String) =
        repository.createReply(topicId, content, username)
}

class UploadImageUseCase(private val repository: ImageUploadRepository) {
    suspend operator fun invoke(contentUri: String) = repository.upload(contentUri)
}

class LoadReplyDraftUseCase(private val repository: ReplyDraftRepository) {
    suspend operator fun invoke(topicId: Long) = repository.load(topicId)
}

class SaveReplyDraftUseCase(private val repository: ReplyDraftRepository) {
    suspend operator fun invoke(draft: ReplyDraft) = repository.save(draft)
}

class AddReplyDraftImageUseCase(private val repository: ReplyDraftRepository) {
    suspend operator fun invoke(topicId: Long, image: UploadedReplyImage) =
        repository.addImage(topicId, image)
}

class ClearReplyDraftUseCase(private val repository: ReplyDraftRepository) {
    suspend operator fun invoke(topicId: Long) = repository.clear(topicId)
}
