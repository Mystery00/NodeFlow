package app.mystery0.nodeflow.imagehosting.imgur

import app.mystery0.nodeflow.imagehosting.contract.*
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

class ImgurHttpImageHostAdapterTest {
    private lateinit var server: MockWebServer
    private lateinit var adapter: ImgurHttpImageHostAdapter

    @Before fun setUp() {
        server = MockWebServer().also { it.start() }
        val base = server.url("/")
        adapter = ImgurHttpImageHostAdapter(
            client = OkHttpClient.Builder().followRedirects(false).build(),
            pageBaseUrl = base,
            apiBaseUrl = base,
            allowedHosts = setOf(base.host),
        )
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun descriptorSupportsImgurFormats() {
        assertThat(adapter.descriptor.enabled).isTrue()
        assertThat(adapter.descriptor.capabilities.mimeTypes).containsExactly(
            "image/png", "image/jpeg", "image/gif", "image/webp",
        )
        assertThat(adapter.descriptor.capabilities.maxBytes).isGreaterThan(0L)
    }

    @Test fun extractsClientIdFromPageAndUploadsToAlbum() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><script>const client_id = 'public-test-client';</script></html>"))
        server.enqueue(MockResponse().setBody("{\"success\":true,\"data\":{\"id\":\"album-id\",\"deletehash\":\"album-delete\"}}"))
        server.enqueue(MockResponse().setBody("{\"success\":true,\"data\":{\"ok\":true}}"))
        server.enqueue(MockResponse().setBody("{\"success\":true,\"data\":{\"id\":\"image-id\",\"link\":\"https://i.imgur.com/image-id.png\",\"deletehash\":\"image-delete\"}}"))
        server.enqueue(MockResponse().setBody("{\"success\":true,\"data\":{\"ok\":true}}"))

        val result = adapter.upload(UploadImage("x.png", "image/png", byteArrayOf(1, 2, 3))) as UploadResult.Success
        assertThat(result.image.remoteId).isEqualTo("image-id")
        assertThat(result.image.directUrl).isEqualTo("https://i.imgur.com/image-id.png")
        assertThat(result.image.displayPageUrl).isEqualTo("https://imgur.com/a/album-id")
        assertThat(server.takeRequest().path).isEqualTo("/upload")
        assertThat(server.takeRequest().path).contains("/3/album?client_id=public-test-client")
        assertThat(server.takeRequest().path).contains("/3/upload/checkcaptcha?client_id=public-test-client")
        val upload = server.takeRequest()
        assertThat(upload.path).contains("/3/upload?client_id=public-test-client")
        val uploadBody = upload.body.readUtf8()
        assertThat(uploadBody).contains("filename=\"x.png\"")
        assertThat(uploadBody).contains("name=\"album\"")
        assertThat(uploadBody).contains("album-delete")
        assertThat(server.takeRequest().path).contains("/3/album/album-delete?client_id=public-test-client")
    }

    @Test fun captchaRequiresInteractionWithoutRetryingUpload() = runBlocking {
        server.enqueue(MockResponse().setBody("captcha verification required"))
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.InteractionRequired)
        assertThat(result.error.certainty).isEqualTo(ResultCertainty.NotSubmitted)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun missingDirectLinkIsProtocolChangedAfterSend() = runBlocking {
        server.enqueue(MockResponse().setBody("client_id=public-test-client"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"id\":\"album\",\"deletehash\":\"album-delete\"}}"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"ok\":true}}"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"id\":\"image\",\"deletehash\":\"image-delete\"}}"))
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.ProtocolChanged)
        assertThat(result.error.certainty).isEqualTo(ResultCertainty.Unknown)
    }

    @Test fun missingDeletehashStillSucceeds() = runBlocking {
        server.enqueue(MockResponse().setBody("client_id=public-test-client"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"id\":\"album\",\"deletehash\":\"album-delete\"}}"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"ok\":true}}"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"id\":\"image\",\"link\":\"https://i.imgur.com/image.png\"}}"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"ok\":true}}"))
        val result = adapter.upload(sample()) as UploadResult.Success
        assertThat(result.image.displayPageUrl).isEqualTo("https://imgur.com/a/album")
    }

    @Test fun missingAlbumDeletehashIsProtocolChangedBeforeUpload() = runBlocking {
        server.enqueue(MockResponse().setBody("client_id=public-test-client"))
        server.enqueue(MockResponse().setBody("{\"data\":{\"id\":\"album\"}}"))
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.ProtocolChanged)
        assertThat(result.error.certainty).isEqualTo(ResultCertainty.NotSubmitted)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun albumChallengeRequiresInteraction() = runBlocking {
        server.enqueue(MockResponse().setBody("client_id=public-test-client"))
        server.enqueue(MockResponse().setBody("captcha verification required"))
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.InteractionRequired)
        assertThat(result.error.recoveryAction).isEqualTo(RecoveryAction.OpenHostPage("https://imgur.com/upload"))
    }

    @Test fun rateLimitPreservesRetryAfter() = runBlocking {
        server.enqueue(MockResponse().setBody("client_id=public-test-client"))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "17"))
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.RateLimited)
        assertThat(result.error.retryAfterSeconds).isEqualTo(17L)
    }

    @Test fun redirectIsRejectedWithoutFollowing() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://evil.example/upload"))
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.ProtocolChanged)
        assertThat(server.requestCount).isEqualTo(1)
    }

    @Test fun networkFailureBeforeUploadIsNetwork() = runBlocking {
        server.shutdown()
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.Network)
        assertThat(result.error.certainty).isEqualTo(ResultCertainty.NotSubmitted)
    }

    @Test fun cancellationPropagates() = runBlocking {
        val cancellation = CancellationException("cancelled")
        val cancelling = ImgurHttpImageHostAdapter(
            client = OkHttpClient.Builder().addInterceptor { throw cancellation }.build(),
            pageBaseUrl = server.url("/"), allowedHosts = setOf(server.hostName),
        )
        assertThat(runCatching { cancelling.upload(sample()) }.exceptionOrNull()).isSameInstanceAs(cancellation)
    }

    @Test fun defaultCookiesAreMergedScopedAndIsolatedBetweenClients() = runBlocking {
        fun defaultAdapter() = ImgurHttpImageHostAdapter(pageBaseUrl = server.url("/"),
            apiBaseUrl = server.url("/"), allowedHosts = setOf(server.hostName))
        val first = defaultAdapter()
        server.enqueue(MockResponse().setHeader("Set-Cookie", "page=one; Path=/upload").setBody("client_id=test"))
        server.enqueue(MockResponse().setResponseCode(500).setHeader("Set-Cookie", "api=two; Path=/"))
        server.enqueue(MockResponse().setBody("client_id=test"))
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(500))
        first.upload(sample())
        first.upload(sample())
        defaultAdapter().upload(sample())
        assertThat(server.takeRequest().getHeader("Cookie")).isNull()
        assertThat(server.takeRequest().getHeader("Cookie")).isNull()
        assertThat(server.takeRequest().getHeader("Cookie")).contains("page=one")
        assertThat(server.takeRequest().getHeader("Cookie")).isEqualTo("api=two")
        assertThat(server.takeRequest().getHeader("Cookie")).isNull()
    }

    @Test fun normalScriptNamesDoNotTriggerChallengeDetection() = runBlocking {
        server.enqueue(MockResponse().setBody("<script src='/challenge.js'></script><script>client_id='test'</script>"))
        server.enqueue(MockResponse().setResponseCode(500))
        val result = adapter.upload(sample()) as UploadResult.Failure
        assertThat(result.error.category).isEqualTo(FailureCategory.Server)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test fun completionIOExceptionKeepsCompletionStage() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (chain.request().method == "PUT") throw java.io.IOException("offline")
            chain.proceed(chain.request())
        }.build()
        val completing = ImgurHttpImageHostAdapter(client, server.url("/"), server.url("/"), setOf(server.hostName))
        server.enqueue(MockResponse().setBody("client_id=test"))
        server.enqueue(MockResponse().setBody("""{"data":{"id":"album","deletehash":"secret"}}"""))
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("""{"data":{"id":"image","link":"https://i.imgur.com/image.png"}}"""))
        val result = completing.upload(sample()) as UploadResult.Failure
        assertThat(result.error.stage).isEqualTo(RequestStage.Completion)
        assertThat(result.error.certainty).isEqualTo(ResultCertainty.Unknown)
    }

    private fun sample() = UploadImage("x.png", "image/png", byteArrayOf(1, 2, 3))
}
