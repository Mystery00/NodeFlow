package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class V2exImageRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: V2exImageRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .build()
            .create(V2exWriteApi::class.java)
        dataSource = V2exImageRemoteDataSource(api, V2exHtmlParser(), server.url("/")) { 123L }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun upload_preflightsAndPostsQqfileOnce() = runTest {
        server.enqueue(MockResponse().setBody("""<form action="/i/upload"><input type="file" name="qqfile" /></form>"""))
        server.enqueue(MockResponse().setBody("""{"success":"true","name":"sample","uri":"sample.png","url_o":"//i.v2ex.co/sample.png"}"""))

        val result = dataSource.upload(ImageUploadPayload("test.png", "image/png", byteArrayOf(1, 2)))

        assertThat(result).isInstanceOf(ImageUploadResult.Success::class.java)
        val get = server.takeRequest()
        val post = server.takeRequest()
        assertThat(get.path).isEqualTo("/i/upload")
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.getHeader("X-Requested-With")).isEqualTo("XMLHttpRequest")
        assertThat(post.body.readUtf8()).contains("name=\"qqfile\"")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun upload_classifiesQuotaFailureWithoutRetry() = runTest {
        server.enqueue(MockResponse().setBody("""<form action="/i/upload"><input type="file" name="qqfile" /></form>"""))
        server.enqueue(MockResponse().setBody("""{"success":"false","message":"图片额度不足"}"""))

        val result = dataSource.upload(ImageUploadPayload("test.png", "image/png", byteArrayOf(1)))

        result as ImageUploadResult.Failure
        assertThat(result.reason).isEqualTo(ImageUploadFailureReason.QuotaExceeded)
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun upload_doesNotRepeatPostWhenServerRequestsImmediateRetry() = runTest {
        server.enqueue(MockResponse().setBody("""<form action="/i/upload"><input type="file" name="qqfile" /></form>"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .addHeader("Retry-After", "0")
                .setBody("temporarily unavailable"),
        )
        server.enqueue(MockResponse().setBody("""{"success":"true","url_o":"//i.v2ex.co/retried.png"}"""))

        runCatching { dataSource.upload(ImageUploadPayload("test.png", "image/png", byteArrayOf(1))) }

        assertThat(server.requestCount).isEqualTo(2)
        assertThat(server.takeRequest().method).isEqualTo("GET")
        assertThat(server.takeRequest().method).isEqualTo("POST")
    }
}
