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
    suspend fun upload(hostId: app.mystery0.nodeflow.imagehosting.contract.ImageHostId, contentUri: String): ImageUploadResult =
        if (hostId.value == "v2ex") upload(contentUri)
        else ImageUploadResult.Failure(ImageUploadFailureReason.HostUnavailable, "所选图床不可用，请重新选择")
}
