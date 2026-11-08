package app.mystery0.nodeflow.imagehosting.imgur

import app.mystery0.nodeflow.imagehosting.contract.*
import kotlinx.coroutines.CancellationException
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 使用 Imgur 网页公开流程的独立 HTTP 图床客户端，不共享 V2EX 会话。 */
class ImgurHttpImageHostAdapter(
    private val client: OkHttpClient = defaultClient(),
    private val pageBaseUrl: HttpUrl = "https://imgur.com/".toHttpUrl(),
    private val apiBaseUrl: HttpUrl = "https://api.imgur.com/".toHttpUrl(),
    private val allowedHosts: Set<String> = setOf("imgur.com", "api.imgur.com", "i.imgur.com"),
) : ImageHostAdapter {
    override val descriptor = ImageHostDescriptor(
        id = ImageHostId("imgur"),
        displayName = "Imgur",
        capabilities = ImageHostCapabilities(
            mimeTypes = setOf("image/png", "image/jpeg", "image/gif", "image/webp"),
            maxBytes = 10L * 1024 * 1024,
            authentication = Authentication.None,
        ),
    )

    override suspend fun upload(image: UploadImage): UploadResult {
        if (!descriptor.capabilities.supports(image)) {
            return failure(
                if (image.mimeType in descriptor.capabilities.mimeTypes) FailureCategory.FileTooLarge else FailureCategory.UnsupportedType,
                RequestStage.Preparation,
                ResultCertainty.NotSubmitted,
            )
        }
        val clientId = try {
            val page = executeGet(pageBaseUrl.resolve("upload") ?: return protocol(RequestStage.Preparation, ResultCertainty.NotSubmitted))
            if (page.code !in 200..299) return responseFailure(page, RequestStage.Preparation, ResultCertainty.NotSubmitted)
            val html = page.body
            if (isChallenge(html)) return failure(FailureCategory.InteractionRequired, RequestStage.Preparation, ResultCertainty.NotSubmitted, RecoveryAction.OpenHostPage("https://imgur.com/upload"))
            extractClientId(html) ?: fetchScriptClientId(html)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return failure(FailureCategory.Network, RequestStage.Preparation, ResultCertainty.NotSubmitted)
        } ?: return protocol(RequestStage.Preparation, ResultCertainty.NotSubmitted)

        val album = try {
            val response = executePost(
                apiBaseUrl.resolve("3/album")!!.newBuilder().addQueryParameter("client_id", clientId).build(),
                "".toRequestBody("application/json".toMediaType()),
            )
            if (response.code !in 200..299) return responseFailure(response, RequestStage.Upload, ResultCertainty.NotSubmitted)
            val body = response.body
            if (isChallenge(body)) return interaction(RequestStage.Upload, ResultCertainty.NotSubmitted)
            val id = parseField(body, "id")
            val deleteHash = parseField(body, "deletehash")
            if (id.isNullOrBlank() || deleteHash.isNullOrBlank()) return protocol(RequestStage.Upload, ResultCertainty.NotSubmitted)
            AlbumCredentials(id, deleteHash)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return failure(FailureCategory.Network, RequestStage.Upload, ResultCertainty.NotSubmitted)
        }

        try {
            val captcha = executePost(
                apiBaseUrl.resolve("3/upload/checkcaptcha")!!.newBuilder().addQueryParameter("client_id", clientId).build(),
                "".toRequestBody("application/json".toMediaType()),
            )
            if (captcha.code !in 200..299) return responseFailure(captcha, RequestStage.Upload, ResultCertainty.NotSubmitted)
            if (isChallenge(captcha.body)) return interaction(RequestStage.Upload, ResultCertainty.NotSubmitted)
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            return failure(FailureCategory.Network, RequestStage.Upload, ResultCertainty.NotSubmitted)
        }

        var stage = RequestStage.Upload
        return try {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("album", album.deleteHash)
                .addFormDataPart("type", "file")
                .addFormDataPart("image", image.fileName, image.bytes.toRequestBody(image.mimeType.toMediaType()))
                .build()
            val response = executePost(
                apiBaseUrl.resolve("3/upload")!!.newBuilder().addQueryParameter("client_id", clientId).build(), body,
            )
            val responseBody = response.body
            if (isChallenge(responseBody)) return interaction(RequestStage.Upload, ResultCertainty.Unknown)
            if (response.code !in 200..299) return responseFailure(response, RequestStage.Upload, ResultCertainty.Unknown)
            val id = parseField(responseBody, "id")
            val link = parseField(responseBody, "link")
            if (id.isNullOrBlank() || !isDirectImageUrl(link)) return protocol(RequestStage.Completion, ResultCertainty.Unknown)

            stage = RequestStage.Completion
            val completion = executePut(
                apiBaseUrl.resolve("3/album/${album.deleteHash}")!!.newBuilder().addQueryParameter("client_id", clientId).build(),
                "".toRequestBody("application/json".toMediaType()),
            )
            val completionBody = completion.body
            if (isChallenge(completionBody)) return interaction(RequestStage.Completion, ResultCertainty.Unknown)
            if (completion.code !in 200..299) return responseFailure(completion, RequestStage.Completion, ResultCertainty.Unknown)
            UploadResult.Success(UploadedImage(descriptor.id, id, link!!, "https://imgur.com/a/${album.id}", image.mimeType))
        } catch (error: CancellationException) {
            throw error
        } catch (_: IOException) {
            failure(FailureCategory.Network, stage, ResultCertainty.Unknown)
        }
    }

    private data class AlbumCredentials(val id: String, val deleteHash: String)

    private fun fetchScriptClientId(html: String): String? {
        val scripts = Regex("""<script[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { it.groupValues.getOrNull(1) }
        for (src in scripts) {
            val url = pageBaseUrl.resolve(src) ?: continue
            if (url.host !in allowedHosts) continue
            try {
                val response = executeGet(url)
                if (response.code in 200..299) extractClientId(response.body)?.let { return it }
            } catch (error: CancellationException) { throw error } catch (_: IOException) { return null }
        }
        return null
    }

    private fun extractClientId(text: String): String? = listOf(
        Regex("""(?:client_id|clientId)\s*[:=]\s*["']([A-Za-z0-9_-]+)["']""", RegexOption.IGNORE_CASE),
        Regex("(?:[?&]|\\b)client_id=([A-Za-z0-9_-]+)", RegexOption.IGNORE_CASE),
    ).firstNotNullOfOrNull { regex -> regex.find(text)?.groupValues?.getOrNull(1) }

    private fun parseField(json: String, name: String): String? = Regex(""""$name"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)

    private fun isDirectImageUrl(value: String?): Boolean {
        val url = value?.let { runCatching { it.toHttpUrl() }.getOrNull() } ?: return false
        return url.isHttps && url.host == "i.imgur.com" && url.pathSegments.lastOrNull()?.contains('.') == true
    }

    private fun isChallenge(body: String): Boolean {
        val document = org.jsoup.Jsoup.parse(body)
        return document.title().contains("Just a moment", true) ||
            document.selectFirst("#challenge-form, #cf-challenge-running, iframe[src*=recaptcha], iframe[src*=hcaptcha]") != null ||
            Regex("captcha\\s+(verification\\s+)?required|verify you are human|[\"'](?:captcha|recaptcha|challenge)[\"']\\s*:\\s*true", RegexOption.IGNORE_CASE).containsMatchIn(body)
    }

    private fun executeGet(url: HttpUrl) = execute(Request.Builder().url(checked(url)).get().build())
    private fun executePost(url: HttpUrl, body: okhttp3.RequestBody) = execute(Request.Builder().url(checked(url)).post(body).build())
    private fun executePut(url: HttpUrl, body: okhttp3.RequestBody) = execute(Request.Builder().url(checked(url)).put(body).build())
    private data class HostResponse(val code: Int, val body: String, val retryAfter: String?)

    private fun execute(request: Request): HostResponse = client.newCall(request).execute().use {
        HostResponse(it.code, it.body.string(), it.header("Retry-After"))
    }
    private fun checked(url: HttpUrl): HttpUrl = url.also { require(it.host in allowedHosts && (it.scheme == "https" || it.host == pageBaseUrl.host)) { "Imgur host is not allowed" } }

    private fun responseFailure(response: HostResponse, stage: RequestStage, certainty: ResultCertainty): UploadResult {
        val body = response.body
        if (isChallenge(body)) return interaction(stage, certainty)
        if (response.code == 429) {
            val retryAfter = response.retryAfter?.trim()?.toLongOrNull()?.takeIf { it >= 0 }
            return UploadResult.Failure(UploadFailure(descriptor.id, FailureCategory.RateLimited, stage, certainty, retryAfterSeconds = retryAfter))
        }
        val category = when (response.code) {
            in 400..499 -> FailureCategory.PermissionDenied
            in 500..599 -> FailureCategory.Server
            else -> FailureCategory.ProtocolChanged
        }
        return failure(category, stage, certainty)
    }

    private fun failure(category: FailureCategory, stage: RequestStage, certainty: ResultCertainty, action: RecoveryAction = RecoveryAction.None) = UploadResult.Failure(UploadFailure(descriptor.id, category, stage, certainty, recoveryAction = action))
    private fun interaction(stage: RequestStage, certainty: ResultCertainty) = failure(FailureCategory.InteractionRequired, stage, certainty, RecoveryAction.OpenHostPage("https://imgur.com/upload"))
    private fun protocol(stage: RequestStage, certainty: ResultCertainty) = failure(FailureCategory.ProtocolChanged, stage, certainty)

    companion object {
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .cookieJar(ImgurCookieJar())
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .callTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}

/** 独立图床会话遵循 Cookie 的域、路径、安全性与过期规则。 */
internal class ImgurCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()
    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
        cookies.removeAll { it.expiresAt <= System.currentTimeMillis() }
        cookies.filter { it.matches(url) }.sortedByDescending { it.path.length }
    }
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this.cookies) {
            cookies.forEach { incoming ->
                this.cookies.removeAll { it.name == incoming.name && it.domain == incoming.domain && it.path == incoming.path }
                if (incoming.expiresAt > System.currentTimeMillis()) this.cookies.add(incoming)
            }
        }
    }
}
