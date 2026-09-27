package app.mystery0.nodeflow.imagehosting.v2ex

import app.mystery0.nodeflow.imagehosting.contract.*

class V2exImageHostAdapter(
    private val transport: V2exImageTransport,
    private val classifier: V2exPageAccessClassifier = V2exImageParser(),
    private val responseParser: V2exImageResponseParser = V2exImageParser(),
) : ImageHostAdapter {
    override val descriptor = ImageHostDescriptor(
        ImageHostId("v2ex"), "V2EX", ImageHostCapabilities(setOf("image/png", "image/jpeg", "image/gif", "image/webp"), 6L * 1024 * 1024),
    )

    override suspend fun upload(image: UploadImage): UploadResult {
        if (!descriptor.capabilities.supports(image)) return UploadResult.Failure(
            UploadFailure(descriptor.id, if (image.mimeType in descriptor.capabilities.mimeTypes) FailureCategory.FileTooLarge else FailureCategory.UnsupportedType, RequestStage.Preparation, ResultCertainty.NotSubmitted)
        )
        val page = transport.getUploadPage()
        if (page.statusCode !in 200..299) return failure(FailureCategory.Server, RequestStage.Preparation, ResultCertainty.NotSubmitted)
        return when (classifier.classifyUploadPage(page.finalUrl, page.body)) {
            V2exUploadPageAccess.AuthenticationRequired -> failure(FailureCategory.AuthenticationRequired, RequestStage.Preparation, ResultCertainty.NotSubmitted, RecoveryAction.SignInToHost)
            V2exUploadPageAccess.Challenge, V2exUploadPageAccess.Unrecognized -> failure(FailureCategory.InteractionRequired, RequestStage.Preparation, ResultCertainty.NotSubmitted, RecoveryAction.OpenHostPage("https://www.v2ex.com/i"))
            V2exUploadPageAccess.Available -> uploadOnce(image)
        }
    }

    private suspend fun uploadOnce(image: UploadImage): UploadResult {
        val response = transport.uploadImage(image.fileName, image.mimeType, image.bytes, "https://www.v2ex.com/i/upload")
        if (response.statusCode !in 200..299) return failure(FailureCategory.Server, RequestStage.Upload, ResultCertainty.Unknown)
        return when (classifier.classifyUploadResponse(response.finalUrl, response.body)) {
            V2exUploadResponseAccess.AuthenticationRequired -> failure(FailureCategory.AuthenticationRequired, RequestStage.Upload, ResultCertainty.Unknown, RecoveryAction.SignInToHost)
            V2exUploadResponseAccess.Challenge -> failure(FailureCategory.InteractionRequired, RequestStage.Upload, ResultCertainty.Unknown, RecoveryAction.OpenHostPage("https://www.v2ex.com/i"))
            V2exUploadResponseAccess.Unrecognized -> failure(FailureCategory.ProtocolChanged, RequestStage.Upload, ResultCertainty.Unknown)
            V2exUploadResponseAccess.Normal -> {
                val parsed = responseParser.parse(response.body, response.contentType)
                if (parsed == null) {
                    val category = if (response.body.contains("额度") || response.body.contains("quota", true)) FailureCategory.QuotaExceeded else if (response.body.contains("权限")) FailureCategory.PermissionDenied else FailureCategory.ProtocolChanged
                    failure(category, RequestStage.Completion, ResultCertainty.Unknown)
                } else {
                    val actualMime = response.contentType?.substringBefore(';')?.lowercase()
                        ?.takeUnless { it == "application/json" }
                        ?: when (parsed.originalUrl.substringAfterLast('.', "").lowercase()) {
                            "png" -> "image/png"
                            "jpg", "jpeg" -> "image/jpeg"
                            "gif" -> "image/gif"
                            "webp" -> "image/webp"
                            else -> null
                        }
                    if (actualMime == null || actualMime !in descriptor.capabilities.mimeTypes) failure(FailureCategory.ProtocolChanged, RequestStage.Completion, ResultCertainty.Unknown)
                    else UploadResult.Success(UploadedImage(descriptor.id, parsed.imageId, parsed.originalUrl, parsed.detailUrl, actualMime))
                }
            }
        }
    }

    private fun failure(category: FailureCategory, stage: RequestStage, certainty: ResultCertainty, action: RecoveryAction = RecoveryAction.None) = UploadResult.Failure(UploadFailure(descriptor.id, category, stage, certainty, recoveryAction = action))
}
