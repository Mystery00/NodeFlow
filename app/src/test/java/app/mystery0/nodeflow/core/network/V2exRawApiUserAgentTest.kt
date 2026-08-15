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
    fun allTopicsHtml_requestsAllTabWithDesktopUserAgent() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.allTopicsHtml()

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/?tab=all")
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.DESKTOP)
    }

    @Test
    fun topicHtml_usesDesktopUserAgent() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.topicHtml(topicId = 1226421, page = null)

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/t/1226421")
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.DESKTOP)
    }

    @Test
    fun otherRequests_useMobileUserAgent() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.dailyMission()

        val request = server.takeRequest()
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.MOBILE)
    }

    @Test
    fun favoriteTopic_issuesRequestWithCorrectPathAndDesktopUa() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.favoriteTopic(
            topicId = 123,
            once = "99999",
            referer = "https://www.v2ex.com/t/123",
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/favorite/topic/123?once=99999")
        assertThat(request.getHeader("Referer")).isEqualTo("https://www.v2ex.com/t/123")
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.DESKTOP)
    }

    @Test
    fun unfavoriteTopic_issuesRequestWithCorrectPathAndDesktopUa() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.unfavoriteTopic(
            topicId = 456,
            once = "88888",
            referer = "https://www.v2ex.com/t/456",
        )

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/unfavorite/topic/456?once=88888")
        assertThat(request.getHeader("Referer")).isEqualTo("https://www.v2ex.com/t/456")
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.DESKTOP)
    }

    @Test
    fun redeemDailyMission_usesObservedPathAndReferer() = runTest {
        server.enqueue(MockResponse().setBody(""))

        api.redeemDailyMission(once = "84830")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("GET")
        assertThat(request.path).isEqualTo("/mission/daily/redeem?once=84830")
        assertThat(request.getHeader("Referer")).isEqualTo("https://www.v2ex.com/mission/daily")
        assertThat(request.getHeader("User-Agent")).isEqualTo(V2exUserAgents.MOBILE)
    }
}
