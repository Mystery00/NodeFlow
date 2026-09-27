package app.mystery0.nodeflow.imagehosting.v2ex

data class V2exHttpResponse(
    val finalUrl: String,
    val statusCode: Int,
    val body: String,
    val contentType: String? = null,
)

interface V2exImageTransport {
    suspend fun getUploadPage(): V2exHttpResponse
    suspend fun uploadImage(fileName: String, mimeType: String, bytes: ByteArray, referer: String): V2exHttpResponse
}

enum class V2exUploadPageAccess { Available, AuthenticationRequired, Challenge, Unrecognized }

enum class V2exUploadResponseAccess { Normal, AuthenticationRequired, Challenge, Unrecognized }

interface V2exImageResponseParser {
    fun parse(body: String, contentType: String?): ParsedV2exImage?
}

interface V2exPageAccessClassifier {
    fun classifyUploadPage(finalUrl: String, body: String): V2exUploadPageAccess
    fun classifyUploadResponse(finalUrl: String, body: String): V2exUploadResponseAccess
}
