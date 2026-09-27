package app.mystery0.nodeflow.imagehosting.contract

@JvmInline
value class ImageHostId(val value: String) {
    init {
        require(value.isNotBlank()) { "图床 ID 不能为空" }
        require(':' !in value) { "图床 ID 不能包含冒号" }
    }
}

data class ImageHostCapabilities(
    val mimeTypes: Set<String>,
    val maxBytes: Long,
    val authentication: Authentication = Authentication.None,
) {
    fun supports(image: UploadImage): Boolean =
        image.mimeType in mimeTypes && image.bytes.size.toLong() <= maxBytes
}

enum class Authentication { None, HostSession }

data class ImageHostDescriptor(
    val id: ImageHostId,
    val displayName: String,
    val capabilities: ImageHostCapabilities,
    val enabled: Boolean = true,
)

data class UploadImage(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class UploadedImage(
    val hostId: ImageHostId,
    val remoteId: String,
    val directUrl: String,
    val displayPageUrl: String? = null,
    val actualMimeType: String,
)

enum class FailureCategory {
    UnsupportedType, FileTooLarge, UnreadableFile, AuthenticationRequired,
    PermissionDenied, RateLimited, QuotaExceeded, InteractionRequired,
    ProtocolChanged, Network, Server, HostUnavailable,
}

enum class RequestStage { Preparation, Upload, Completion }

enum class ResultCertainty { NotSubmitted, Rejected, Unknown }

sealed interface RecoveryAction {
    data object None : RecoveryAction
    data object SignInToHost : RecoveryAction
    data class OpenHostPage(val url: String) : RecoveryAction
}

data class UploadFailure(
    val hostId: ImageHostId,
    val category: FailureCategory,
    val stage: RequestStage,
    val certainty: ResultCertainty,
    val retryAfterSeconds: Long? = null,
    val recoveryAction: RecoveryAction = RecoveryAction.None,
)

sealed interface UploadResult {
    data class Success(val image: UploadedImage) : UploadResult
    data class Failure(val error: UploadFailure) : UploadResult
}

interface ImageHostAdapter {
    val descriptor: ImageHostDescriptor
    suspend fun upload(image: UploadImage): UploadResult
}
