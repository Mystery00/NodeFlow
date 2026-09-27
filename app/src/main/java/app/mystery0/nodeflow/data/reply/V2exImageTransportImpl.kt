package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.network.asOneShot
import app.mystery0.nodeflow.imagehosting.v2ex.V2exHttpResponse
import app.mystery0.nodeflow.imagehosting.v2ex.V2exImageTransport
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

class V2exImageTransportImpl(
    private val api: V2exWriteApi,
    private val baseUrl: HttpUrl = "https://www.v2ex.com/".toHttpUrl(),
) : V2exImageTransport {
    override suspend fun getUploadPage(): V2exHttpResponse {
        val response = api.getHtml(baseUrl.resolve("i/upload").toString())
        return response.toImageResponse()
    }

    override suspend fun uploadImage(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        referer: String,
    ): V2exHttpResponse {
        val body = bytes.toRequestBody(mimeType.toMediaType())
        val part = MultipartBody.Part.createFormData("qqfile", fileName, body)
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addPart(part)
            .build()
            .asOneShot()
        return api.uploadImage(multipart, referer = referer).toImageResponse()
    }

    private fun retrofit2.Response<okhttp3.ResponseBody>.toImageResponse(): V2exHttpResponse {
        val successBody = body()
        val errorResponseBody = errorBody()
        val contentType = successBody?.contentType()?.toString() ?: errorResponseBody?.contentType()?.toString()
        val responseBody = successBody?.use { it.string() } ?: errorResponseBody?.use { it.string() }.orEmpty()
        return V2exHttpResponse(
            finalUrl = raw().request.url.toString(),
            statusCode = code(),
            body = responseBody,
            contentType = contentType,
        )
    }
}
