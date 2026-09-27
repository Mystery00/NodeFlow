package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.domain.reply.ImageUploadRepository
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.imagehosting.contract.*
import app.mystery0.nodeflow.imagehosting.registry.ImageHostRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageUploadRepositoryImpl(
    private val reader: ImageContentReader,
    private val registry: ImageHostRegistry,
    private val ioDispatcher: CoroutineDispatcher,
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) : ImageUploadRepository {
    constructor(
        reader: ImageContentReader,
        uploadRemote: suspend (ImageUploadPayload) -> ImageUploadResult,
        ioDispatcher: CoroutineDispatcher,
    ) : this(reader, app.mystery0.nodeflow.imagehosting.registry.DefaultImageHostRegistry(listOf(
        object : ImageHostAdapter {
            override val descriptor = ImageHostDescriptor(ImageHostId("v2ex"), "V2EX", ImageHostCapabilities(setOf("image/png", "image/jpeg", "image/gif", "image/webp"), 6L * 1024 * 1024))
            override suspend fun upload(image: UploadImage) = when (val result = uploadRemote(ImageUploadPayload(image.fileName, image.mimeType, image.bytes))) {
                is ImageUploadResult.Success -> UploadResult.Success(UploadedImage(descriptor.id, result.image.imageId, result.image.originalUrl, result.image.detailUrl, image.mimeType))
                is ImageUploadResult.Failure -> UploadResult.Failure(UploadFailure(descriptor.id, FailureCategory.Server, RequestStage.Upload, ResultCertainty.Unknown))
            }
        },
    )), ioDispatcher)

    override suspend fun upload(contentUri: String): ImageUploadResult = upload(ImageHostId("v2ex"), contentUri)

    override suspend fun upload(hostId: ImageHostId, contentUri: String): ImageUploadResult = withContext(ioDispatcher) {
        val adapter = registry.find(hostId) ?: return@withContext failure(ImageUploadFailureReason.HostUnavailable, "所选图床不可用，请重新选择")
        val content = try {
            reader.read(contentUri)
        } catch (error: CancellationException) {
            throw error
        } catch (_: ImageReadException.UnsupportedType) {
            return@withContext failure(ImageUploadFailureReason.UnsupportedType, "仅支持 PNG、JPG、GIF 或 WebP 图片")
        } catch (_: Exception) {
            return@withContext failure(ImageUploadFailureReason.UnreadableFile, "无法读取所选图片")
        }
        val image = UploadImage(content.fileName, content.mimeType, content.bytes)
        if (!adapter.descriptor.capabilities.supports(image)) {
            val reason = if (content.mimeType !in adapter.descriptor.capabilities.mimeTypes) ImageUploadFailureReason.UnsupportedType else ImageUploadFailureReason.FileTooLarge
            return@withContext failure(reason, if (reason == ImageUploadFailureReason.FileTooLarge) {
                if (hostId.value == "v2ex") "图片不能超过 6 MB" else "图片不能超过图床限制"
            } else "该图床不支持此图片格式")
        }
        try {
            when (val result = adapter.upload(image)) {
                is UploadResult.Success -> ImageUploadResult.Success(
                    UploadedReplyImage(
                        imageId = ReplyImageIdCodec.format(hostId, result.image.remoteId),
                        originalUrl = result.image.directUrl,
                        detailUrl = result.image.displayPageUrl ?: result.image.directUrl,
                        originalFileName = content.fileName,
                        createdAtEpochMillis = currentTimeMillis(),
                    ),
                )
                is UploadResult.Failure -> result.error.toDomainFailure()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: NodeFlowException) {
            if (error.kind == NodeFlowException.Kind.Network) failure(ImageUploadFailureReason.Network, "图片上传失败，请检查网络后重试")
            else failure(ImageUploadFailureReason.Server, "图床服务暂时不可用，请稍后重试")
        } catch (_: IOException) {
            failure(ImageUploadFailureReason.Network, "图片上传失败，请检查网络后重试")
        } catch (_: Exception) {
            failure(ImageUploadFailureReason.Server, "图床服务暂时不可用，请稍后重试")
        }
    }

    private fun UploadFailure.toDomainFailure(): ImageUploadResult.Failure {
        val uncertainAfterSend = certainty == ResultCertainty.Unknown && stage != RequestStage.Preparation
        val reason = when {
            uncertainAfterSend -> ImageUploadFailureReason.UploadUnconfirmed
            category == FailureCategory.AuthenticationRequired -> ImageUploadFailureReason.AuthenticationRequired
            category == FailureCategory.PermissionDenied -> ImageUploadFailureReason.PermissionDenied
            category == FailureCategory.QuotaExceeded -> ImageUploadFailureReason.QuotaExceeded
            category == FailureCategory.UnsupportedType -> ImageUploadFailureReason.UnsupportedType
            category == FailureCategory.FileTooLarge -> ImageUploadFailureReason.FileTooLarge
            category == FailureCategory.UnreadableFile -> ImageUploadFailureReason.UnreadableFile
            category == FailureCategory.Network -> ImageUploadFailureReason.Network
            category == FailureCategory.InteractionRequired || category == FailureCategory.ProtocolChanged -> ImageUploadFailureReason.UploadUnconfirmed
            category == FailureCategory.RateLimited || category == FailureCategory.Server -> ImageUploadFailureReason.Server
            category == FailureCategory.HostUnavailable -> ImageUploadFailureReason.HostUnavailable
            else -> ImageUploadFailureReason.Server
        }
        val action = recoveryAction
        val message = when (reason) {
            ImageUploadFailureReason.AuthenticationRequired -> "登录状态已失效，请重新登录"
            ImageUploadFailureReason.PermissionDenied -> "当前账号不能使用该图床"
            ImageUploadFailureReason.QuotaExceeded -> "图床额度不足"
            ImageUploadFailureReason.UploadUnconfirmed -> if (category == FailureCategory.InteractionRequired && certainty == ResultCertainty.NotSubmitted) {
                "该渠道出现验证码或需要网页交互，不能自动上传，请在网页中完成"
            } else "图片上传结果无法确认，请检查图床状态"
            ImageUploadFailureReason.Network -> "图片上传失败，请检查网络后重试"
            ImageUploadFailureReason.UnsupportedType -> "不支持的图片格式"
            ImageUploadFailureReason.FileTooLarge -> "图片超过图床限制"
            ImageUploadFailureReason.UnreadableFile -> "无法读取所选图片"
            ImageUploadFailureReason.Server -> "图床服务暂时不可用，请稍后重试"
            ImageUploadFailureReason.HostUnavailable -> "所选图床不可用，请重新选择"
        }
        return ImageUploadResult.Failure(reason, message, action)
    }

    private fun failure(reason: ImageUploadFailureReason, message: String) = ImageUploadResult.Failure(reason, message)
}
