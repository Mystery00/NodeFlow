package app.mystery0.nodeflow.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class V2exThankApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: V2exThankApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = Retrofit.Builder().baseUrl(server.url("/")).build().create(V2exThankApi::class.java)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun thankTopic_postsEmptyBodyWithOnceAndAjaxHeaders() = runTest {
        server.enqueue(MockResponse().setBody("{\"success\":true,\"once\":\"next\"}"))

        api.thankTopic(99, "token", "https://www.v2ex.com/t/99")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/thank/topic/99?once=token")
        assertThat(request.bodySize).isEqualTo(0)
        assertThat(request.getHeader("Referer")).isEqualTo("https://www.v2ex.com/t/99")
        assertThat(request.getHeader("X-Requested-With")).isEqualTo("XMLHttpRequest")
    }

    @Test
    fun thankReply_postsReplyIdAndOnce() = runTest {
        server.enqueue(MockResponse().setBody("{\"success\":true,\"once\":\"next\"}"))

        api.thankReply(100, "token", "https://www.v2ex.com/t/99")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/thank/reply/100?once=token")
        assertThat(request.bodySize).isEqualTo(0)
    }
}
