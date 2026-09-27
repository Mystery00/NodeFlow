package app.mystery0.nodeflow.imagehosting.v2ex

import org.jsoup.Jsoup

class V2exImageParser : V2exPageAccessClassifier, V2exImageResponseParser {
    override fun classifyUploadPage(finalUrl: String, body: String): V2exUploadPageAccess {
        if (!trusted(finalUrl) || !validEndpoint(finalUrl)) return V2exUploadPageAccess.Unrecognized
        if (body.contains("Just a moment", true) || body.contains("challenge", true)) return V2exUploadPageAccess.Challenge
        if (finalUrl.endsWith("/signin") || body.contains("action=\"/signin\"") || body.contains("登录")) return V2exUploadPageAccess.AuthenticationRequired
        val document = Jsoup.parse(body)
        val fineUploader = document.select("div#uploader").isNotEmpty() && body.contains("FineUploader") && body.contains("/i/upload")
        return if (document.selectFirst("form[action=/i/upload] input[type=file][name=qqfile]") != null || fineUploader) V2exUploadPageAccess.Available else V2exUploadPageAccess.Unrecognized
    }

    override fun classifyUploadResponse(finalUrl: String, body: String): V2exUploadResponseAccess {
        if (!trusted(finalUrl) || !validEndpoint(finalUrl)) return V2exUploadResponseAccess.Unrecognized
        if (finalUrl.endsWith("/signin") || body.contains("/signin") || body.contains("登录")) return V2exUploadResponseAccess.AuthenticationRequired
        if (body.contains("challenge", true) || body.contains("Just a moment", true)) return V2exUploadResponseAccess.Challenge
        return V2exUploadResponseAccess.Normal
    }

    override fun parse(body: String, contentType: String?): ParsedV2exImage? {
        if (contentType != null && !contentType.lowercase().startsWith("application/json")) return null
        if (!Regex("\"success\"\\s*:\\s*(true|\"true\")").containsMatchIn(body)) return null
        fun field(name: String) = Regex("\"$name\"\\s*:\\s*\"([^\"]+)\"").find(body)?.groupValues?.get(1)
        val id = field("name") ?: return null
        val uri = field("uri") ?: return null
        val url = (field("url_o") ?: return null).let { if (it.startsWith("//")) "https:$it" else it }
        if (!url.startsWith("https://i.v2ex.co/")) return null
        return ParsedV2exImage(id, url, "https://www.v2ex.com/i/$uri")
    }

    private fun validEndpoint(url: String): Boolean = runCatching {
        val parsed = java.net.URI(url)
        parsed.path == "/i/upload" || parsed.path == "/signin"
    }.getOrDefault(false)

    private fun trusted(url: String): Boolean = runCatching {
        val parsed = java.net.URI(url)
        parsed.scheme == "https" && parsed.host == "www.v2ex.com" && (parsed.port == -1 || parsed.port == 443)
    }.getOrDefault(false)
}

data class ParsedV2exImage(val imageId: String, val originalUrl: String, val detailUrl: String)
