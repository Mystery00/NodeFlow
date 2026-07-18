package app.mystery0.nodeflow.domain.reply

data class ReplyConstraints(val maxLength: Int)

data class ReplyDraft(
    val topicId: Long,
    val content: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val images: List<UploadedReplyImage> = emptyList(),
    val updatedAtEpochMillis: Long,
)

data class UploadedReplyImage(
    val imageId: String,
    val originalUrl: String,
    val detailUrl: String,
    val originalFileName: String,
    val createdAtEpochMillis: Long,
)

sealed interface CreateReplyResult {
    data class Success(val floor: Int) : CreateReplyResult

    data class Failure(
        val reason: ReplyFailureReason,
        val message: String,
    ) : CreateReplyResult
}

enum class ReplyFailureReason {
    AuthenticationRequired,
    TopicUnavailable,
    AntiFlood,
    SubmitUnconfirmed,
    Network,
    Server,
    Parse,
}

sealed interface ImageUploadResult {
    data class Success(val image: UploadedReplyImage) : ImageUploadResult

    data class Failure(
        val reason: ImageUploadFailureReason,
        val message: String,
    ) : ImageUploadResult
}

enum class ImageUploadFailureReason {
    AuthenticationRequired,
    PermissionDenied,
    QuotaExceeded,
    UnsupportedType,
    FileTooLarge,
    UnreadableFile,
    UploadUnconfirmed,
    Network,
    Server,
}
