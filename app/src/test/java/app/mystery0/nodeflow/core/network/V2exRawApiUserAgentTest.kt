package app.mystery0.nodeflow.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class V2exRawApiUserAgentTest {
    private lateinit var server: MockWebServer
    private lateinit var api: V2exRawApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client = OkHttpClient.Builder()
            .addInterceptor(UserAgentInterceptor())
            .build()
        api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .build()
            .create(V2exRawApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun nodeTopicsHtml_usesDesktopUserAgent() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.nodeTopicsHtml("python", page = null)

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/go/python")
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.DESKTOP)
    }

    @Test
    fun recentTopicsHtml_usesDesktopUserAgent() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.recentTopicsHtml(page = 2)

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/recent?p=2")
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.DESKTOP)
    }

    @Test
    fun otherRequests_useMobileUserAgent() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.dailyMission()

        val request = server.takeRequest()
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.MOBILE)
    }
}
