package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage
import app.mystery0.nodeflow.imagehosting.contract.FailureCategory
import app.mystery0.nodeflow.imagehosting.contract.UploadImage
import app.mystery0.nodeflow.imagehosting.contract.UploadResult
import app.mystery0.nodeflow.imagehosting.v2ex.V2exImageHostAdapter
import kotlinx.coroutines.CancellationException

class V2exImageUploadDataSource(
    private val adapter: V2exImageHostAdapter,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun upload(payload: ImageUploadPayload): ImageUploadResult {
        val result = try {
            adapter.upload(UploadImage(payload.fileName, payload.mimeType, payload.bytes))
        } catch (error: CancellationException) {
            throw error
        }
        return when (result) {
            is UploadResult.Success -> ImageUploadResult.Success(
                UploadedReplyImage(
                    imageId = result.image.remoteId,
                    originalUrl = result.image.directUrl,
                    detailUrl = result.image.displayPageUrl ?: result.image.directUrl,
                    originalFileName = payload.fileName,
                    createdAtEpochMillis = currentTimeMillis(),
                ),
            )
            is UploadResult.Failure -> result.error.toDomainFailure()
        }
    }

    private fun app.mystery0.nodeflow.imagehosting.contract.UploadFailure.toDomainFailure(): ImageUploadResult.Failure {
        val reason = when (category) {
            FailureCategory.AuthenticationRequired -> ImageUploadFailureReason.AuthenticationRequired
            FailureCategory.PermissionDenied -> ImageUploadFailureReason.PermissionDenied
            FailureCategory.QuotaExceeded -> ImageUploadFailureReason.QuotaExceeded
            FailureCategory.UnsupportedType -> ImageUploadFailureReason.UnsupportedType
            FailureCategory.FileTooLarge -> ImageUploadFailureReason.FileTooLarge
            FailureCategory.InteractionRequired,
            FailureCategory.ProtocolChanged,
            -> ImageUploadFailureReason.UploadUnconfirmed
            FailureCategory.Network -> ImageUploadFailureReason.Network
            FailureCategory.RateLimited -> ImageUploadFailureReason.Server
            FailureCategory.UnreadableFile -> ImageUploadFailureReason.UnreadableFile
            FailureCategory.Server -> ImageUploadFailureReason.Server
            FailureCategory.HostUnavailable -> ImageUploadFailureReason.HostUnavailable
        }
        val message = when (reason) {
            ImageUploadFailureReason.AuthenticationRequired -> "登录状态已失效，请重新登录"
            ImageUploadFailureReason.PermissionDenied -> "当前账号不能使用 V2EX 图片库"
            ImageUploadFailureReason.QuotaExceeded -> "V2EX 图片额度或铜币不足"
            ImageUploadFailureReason.UploadUnconfirmed -> "图片上传结果无法确认，请检查 V2EX 图片库"
            ImageUploadFailureReason.UnsupportedType -> "不支持的图片格式"
            ImageUploadFailureReason.FileTooLarge -> "图片不能超过 6 MB"
            ImageUploadFailureReason.UnreadableFile -> "无法读取所选图片"
            ImageUploadFailureReason.Network -> "图片上传失败，请检查网络后重试"
            ImageUploadFailureReason.Server -> "无法识别 V2EX 图片上传页面，请稍后重试或在网页检查图库状态"
            ImageUploadFailureReason.HostUnavailable -> "所选图床不可用，请重新选择"
        }
        return ImageUploadResult.Failure(reason, message)
    }
}
