package app.mystery0.nodeflow.imagehosting.v2ex

import org.jsoup.Jsoup

class V2exImageParser : V2exPageAccessClassifier, V2exImageResponseParser {
    override fun classifyUploadPage(finalUrl: String, body: String): V2exUploadPageAccess {
        if (!trusted(finalUrl) || !validEndpoint(finalUrl)) return V2exUploadPageAccess.Unrecognized
        val document = Jsoup.parse(body)
        if (isChallenge(document)) return V2exUploadPageAccess.Challenge
        if (java.net.URI(finalUrl).path == "/signin" || document.selectFirst("form[action=/signin], form[action^=/signin?]") != null) return V2exUploadPageAccess.AuthenticationRequired
        val fineUploader = document.selectFirst("div#uploader") != null && document.select("script:not([src])").any {
            val script = it.data()
            Regex("""\bnew\s+qq\.FineUploader\s*\(""").containsMatchIn(script) &&
                Regex("""\belement\s*:\s*document\.getElementById\s*\(\s*["']uploader["']\s*\)""").containsMatchIn(script) &&
                Regex("""\brequest\s*:\s*\{\s*endpoint\s*:\s*["']/i/upload["']""").containsMatchIn(script)
        }
        return if (document.selectFirst("form[action=/i/upload] input[type=file][name=qqfile]") != null || fineUploader) V2exUploadPageAccess.Available else V2exUploadPageAccess.Unrecognized
    }

    override fun classifyUploadResponse(finalUrl: String, body: String): V2exUploadResponseAccess {
        if (!trusted(finalUrl) || !validEndpoint(finalUrl)) return V2exUploadResponseAccess.Unrecognized
        val document = Jsoup.parse(body)
        if (isChallenge(document)) return V2exUploadResponseAccess.Challenge
        if (java.net.URI(finalUrl).path == "/signin" || document.selectFirst("form[action=/signin], form[action^=/signin?]") != null) return V2exUploadResponseAccess.AuthenticationRequired
        return V2exUploadResponseAccess.Normal
    }

    private fun isChallenge(document: org.jsoup.nodes.Document): Boolean =
        document.title().contains("Just a moment", true) ||
            document.selectFirst("#challenge-form, #cf-challenge-running, script[src*=/cdn-cgi/challenge-platform/]") != null

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
