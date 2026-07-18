package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.domain.reply.ImageUploadRepository
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.IOException

class ImageUploadRepositoryImpl(
    private val reader: ImageContentReader,
    private val uploadRemote: suspend (ImageUploadPayload) -> ImageUploadResult,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageUploadRepository {
    override suspend fun upload(contentUri: String): ImageUploadResult = withContext(ioDispatcher) {
        val content = try {
            reader.read(contentUri)
        } catch (_: ImageReadException.UnsupportedType) {
            return@withContext failure(
                ImageUploadFailureReason.UnsupportedType,
                "仅支持 PNG、JPG、GIF 或 WebP 图片",
            )
        } catch (_: Throwable) {
            return@withContext failure(ImageUploadFailureReason.UnreadableFile, "无法读取所选图片")
        }
        if (content.bytes.size > MAX_IMAGE_BYTES) {
            return@withContext failure(ImageUploadFailureReason.FileTooLarge, "图片不能超过 6 MB")
        }
        try {
            uploadRemote(ImageUploadPayload(content.fileName, content.mimeType, content.bytes))
        } catch (error: CancellationException) {
            throw error
        } catch (error: NodeFlowException) {
            if (error.kind == NodeFlowException.Kind.Network) networkFailure() else serverFailure()
        } catch (_: IOException) {
            networkFailure()
        } catch (_: Exception) {
            serverFailure()
        }
    }

    private fun networkFailure() =
        failure(ImageUploadFailureReason.Network, "图片上传失败，请检查网络后重试")

    private fun serverFailure() =
        failure(ImageUploadFailureReason.Server, "图片上传失败，请稍后重试")

    private fun failure(reason: ImageUploadFailureReason, message: String) =
        ImageUploadResult.Failure(reason, message)
}
