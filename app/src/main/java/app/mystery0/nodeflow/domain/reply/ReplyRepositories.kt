package app.mystery0.nodeflow.domain.reply

interface ReplyRepository {
    suspend fun loadConstraints(topicId: Long): Result<ReplyConstraints>

    suspend fun createReply(
        topicId: Long,
        content: String,
    ): CreateReplyResult
}

interface ReplyDraftRepository {
    suspend fun load(topicId: Long): ReplyDraft?
    suspend fun save(draft: ReplyDraft)
    suspend fun addImage(topicId: Long, image: UploadedReplyImage)
    suspend fun clear(topicId: Long)
}

interface ImageUploadRepository {
    suspend fun upload(contentUri: String): ImageUploadResult
}
