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
        val finalUrl = preflight.raw().request.url
        if (!isTrustedOrigin(finalUrl) || finalUrl.encodedPath !in setOf("/i/upload", "/signin")) {
            preflight.body()?.close()
            preflight.errorBody()?.close()
            return pageFailure()
        }
        if (finalUrl.encodedPath == "/signin") {
            preflight.body()?.close()
            preflight.errorBody()?.close()
            return authFailure()
        }
        val page = preflight.bodyStringOrThrow()
        when (parser.parseImageUploadPage(page)) {
            V2exHtmlParser.ParsedImageUploadPage.AuthenticationRequired -> return authFailure()
            V2exHtmlParser.ParsedImageUploadPage.Unrecognized -> return pageFailure()
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
        val responseUrl = response.raw().request.url
        if (!isTrustedOrigin(responseUrl) || responseUrl.encodedPath !in setOf("/i/upload", "/signin")) {
            response.body()?.close()
            response.errorBody()?.close()
            return unconfirmedFailure()
        }
        if (responseUrl.encodedPath == "/signin") {
            response.body()?.close()
            response.errorBody()?.close()
            return authFailure()
        }
        val responseBody = response.bodyStringOrThrow()
        if (parser.hasSignInEntry(responseBody)) return authFailure()
        if (parser.hasAccessChallenge(responseBody)) return unconfirmedFailure()
        val parsed = parser.parseImageUploadResponse(responseBody)
            ?: return when {
                listOf("额度", "余额", "铜币", "quota").any {
                    responseBody.contains(it, ignoreCase = true)
                } -> failure(ImageUploadFailureReason.QuotaExceeded, "V2EX 图片额度或铜币不足")
                responseBody.contains("权限") -> failure(
                    ImageUploadFailureReason.PermissionDenied,
                    "当前账号不能使用 V2EX 图片库",
                )
                else -> unconfirmedFailure()
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

    private fun isTrustedOrigin(url: HttpUrl) =
        url.scheme == baseUrl.scheme && url.host == baseUrl.host && url.port == baseUrl.port

    private fun pageFailure() = failure(
        ImageUploadFailureReason.Server,
        "无法识别 V2EX 图片上传页面，请稍后重试或在网页检查图库状态",
    )

    private fun unconfirmedFailure() = failure(
        ImageUploadFailureReason.UploadUnconfirmed,
        "图片上传结果无法确认，请检查 V2EX 图片库",
    )

    private fun failure(reason: ImageUploadFailureReason, message: String) =
        ImageUploadResult.Failure(reason, message)
}
