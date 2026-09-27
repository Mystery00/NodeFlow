package app.mystery0.nodeflow.imagehosting.v2ex

import app.mystery0.nodeflow.imagehosting.contract.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertThrows
import org.junit.Test

class V2exImageHostAdapterTest {
    private fun image() = UploadImage("x.png", "image/png", byteArrayOf(1))

    @Test fun uploadsQqfileTransportOnceAndParsesResult() = runBlocking {
        var uploads = 0
        val adapter = V2exImageHostAdapter(object : V2exImageTransport {
            override suspend fun getUploadPage() = V2exHttpResponse("https://www.v2ex.com/i/upload", 200, "<form action=\"/i/upload\"><input type=\"file\" name=\"qqfile\"></form>")
            override suspend fun uploadImage(fileName: String, mimeType: String, bytes: ByteArray, referer: String): V2exHttpResponse {
                uploads++
                assertThat(fileName).isEqualTo("x.png")
                assertThat(referer).isEqualTo("https://www.v2ex.com/i/upload")
                return V2exHttpResponse("https://www.v2ex.com/i/upload", 200, "{\"success\":true,\"name\":\"id\",\"uri\":\"id.png\",\"url_o\":\"//i.v2ex.co/id.png\"}", "application/json")
            }
        })
        val result = adapter.upload(image())
        assertThat(result).isInstanceOf(UploadResult.Success::class.java)
        result as UploadResult.Success
        assertThat(result.image.directUrl).isEqualTo("https://i.v2ex.co/id.png")
        assertThat(uploads).isEqualTo(1)
    }

    @Test fun nonSuccessfulPreflightDoesNotUpload() = runBlocking {
        var uploads = 0
        val adapter = V2exImageHostAdapter(object : V2exImageTransport {
            override suspend fun getUploadPage() = V2exHttpResponse("https://www.v2ex.com/i/upload", 503, "<html>busy</html>")
            override suspend fun uploadImage(fileName: String, mimeType: String, bytes: ByteArray, referer: String): V2exHttpResponse { uploads++; error("must not upload") }
        })
        val result = adapter.upload(image()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.Server)
        assertThat(uploads).isEqualTo(0)
    }

    @Test fun nonSuccessfulUploadIsUnknownAndNotRetried() = runBlocking {
        var uploads = 0
        val adapter = V2exImageHostAdapter(object : V2exImageTransport {
            override suspend fun getUploadPage() = V2exHttpResponse("https://www.v2ex.com/i/upload", 200, "<form action=\"/i/upload\"><input type=\"file\" name=\"qqfile\"></form>")
            override suspend fun uploadImage(fileName: String, mimeType: String, bytes: ByteArray, referer: String): V2exHttpResponse { uploads++; return V2exHttpResponse("https://www.v2ex.com/i/upload", 500, "{\"error\":\"busy\"}", "application/json") }
        })
        val result = adapter.upload(image()) as UploadResult.Failure
        assertThat(result.error.certainty).isEqualTo(ResultCertainty.Unknown)
        assertThat(uploads).isEqualTo(1)
    }

    @Test fun customClassifierAndParserAreUsedWithoutConcreteCast() = runBlocking {
        val parser = object : V2exImageResponseParser { override fun parse(body: String, contentType: String?) = ParsedV2exImage("custom", "https://i.v2ex.co/custom.png", "https://www.v2ex.com/i/custom.png") }
        val classifier = object : V2exPageAccessClassifier {
            override fun classifyUploadPage(finalUrl: String, body: String) = V2exUploadPageAccess.Available
            override fun classifyUploadResponse(finalUrl: String, body: String) = V2exUploadResponseAccess.Normal
        }
        val adapter = V2exImageHostAdapter(object : V2exImageTransport {
            override suspend fun getUploadPage() = V2exHttpResponse("https://www.v2ex.com/i/upload", 200, "")
            override suspend fun uploadImage(fileName: String, mimeType: String, bytes: ByteArray, referer: String) = V2exHttpResponse("https://www.v2ex.com/i/upload", 200, "", "image/png")
        }, classifier, parser)
        assertThat((adapter.upload(image()) as UploadResult.Success).image.remoteId).isEqualTo("custom")
    }

    @Test fun propagatesCancellationFromRealTransport() {
        val cancellation = CancellationException("cancel")
        val adapter = V2exImageHostAdapter(object : V2exImageTransport {
            override suspend fun getUploadPage(): V2exHttpResponse = throw cancellation
            override suspend fun uploadImage(fileName: String, mimeType: String, bytes: ByteArray, referer: String): V2exHttpResponse = error("unreachable")
        })
        val thrown = assertThrows(CancellationException::class.java) { runBlocking { adapter.upload(image()) } }
        assertThat(thrown).isSameInstanceAs(cancellation)
    }
}
