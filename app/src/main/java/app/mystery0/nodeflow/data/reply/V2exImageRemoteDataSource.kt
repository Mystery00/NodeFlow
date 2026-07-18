package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.network.asOneShot
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

data class ImageUploadPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

class V2exImageRemoteDataSource(
    private val api: V2exWriteApi,
    private val parser: V2exHtmlParser,
    private val baseUrl: HttpUrl = "https://www.v2ex.com/".toHttpUrl(),
    private val currentTimeMillis: () -> Long = System::currentTimeMillis,
) {
    suspend fun upload(payload: ImageUploadPayload): ImageUploadResult {
        val uploadUrl = baseUrl.resolve("i/upload").toString()
        val preflight = api.getHtml(uploadUrl)
        val finalPath = preflight.raw().request.url.encodedPath
        if (finalPath == "/signin") return authFailure()
        val page = preflight.bodyStringOrThrow()
        when (parser.parseImageUploadPage(page)) {
            V2exHtmlParser.ParsedImageUploadPage.AuthenticationRequired -> return authFailure()
            V2exHtmlParser.ParsedImageUploadPage.PermissionDenied -> return failure(
                ImageUploadFailureReason.PermissionDenied,
                "当前账号不能使用 V2EX 图片库",
            )
            V2exHtmlParser.ParsedImageUploadPage.Available -> Unit
        }
        val body = payload.bytes.toRequestBody(payload.mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("qqfile", payload.fileName, body)
        val multipartBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(part)
            .build()
            .asOneShot()
        val response = api.uploadImage(multipartBody, referer = uploadUrl)
        if (response.raw().request.url.encodedPath == "/signin") return authFailure()
        val responseBody = response.bodyStringOrThrow()
        val parsed = parser.parseImageUploadResponse(responseBody)
            ?: return when {
                listOf("额度", "余额", "铜币", "quota").any {
                    responseBody.contains(it, ignoreCase = true)
                } -> failure(ImageUploadFailureReason.QuotaExceeded, "V2EX 图片额度或铜币不足")
                responseBody.contains("权限") -> failure(
                    ImageUploadFailureReason.PermissionDenied,
                    "当前账号不能使用 V2EX 图片库",
                )
                else -> failure(
                    ImageUploadFailureReason.UploadUnconfirmed,
                    "图片上传结果无法确认，请检查 V2EX 图片库",
                )
            }
        return ImageUploadResult.Success(
            UploadedReplyImage(
                imageId = parsed.imageId,
                originalUrl = parsed.originalUrl,
                detailUrl = parsed.detailUrl,
                originalFileName = payload.fileName,
                createdAtEpochMillis = currentTimeMillis(),
            ),
        )
    }

    private fun authFailure() = failure(
        ImageUploadFailureReason.AuthenticationRequired,
        "登录状态已失效，请重新登录",
    )

    private fun failure(reason: ImageUploadFailureReason, message: String) =
        ImageUploadResult.Failure(reason, message)
}
