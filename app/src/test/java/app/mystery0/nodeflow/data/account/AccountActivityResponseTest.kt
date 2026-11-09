package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.data.node.NodeRemoteDataSource
import app.mystery0.nodeflow.data.topic.TopicRemoteDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Retrofit

/** 通过本地响应拦截替身运行真实 Retrofit 声明和数据源，不访问真实站点。 */
class AccountActivityResponseTest {
    private val parser = V2exHtmlParser()
    private val session = AuthSession(username = "reader", cookieHeader = "test-cookie")
    private val sidebar = """<div id='Rightbar'><div class='box'><a href='/member/reader'><img class='avatar' src='/avatar/sample.png'>reader</a>
        <a href='/my/nodes'>节点收藏</a><a href='/my/topics'>主题收藏</a><a href='/my/following'>特别关注</a>
        <div class='member-activity-bar'><div class='member-activity-fourth' style='width: 59%'></div></div></div></div>"""
    private val list = """<div id='Main'><div class='box'><div class='cell'><h2>收藏的主题</h2></div>
        <div class='cell item'><span class='item_title'><a href='/t/42'>主题</a></span>
        <a href='/member/author'>author</a><a href='/go/android'>Android</a></div></div></div>"""
    private val topic = "<div class='header'><h1>主题</h1></div><div class='topic_content'>正文</div>"

    @Test
    fun everyListAndDetailPublishesFromExistingResponseOnly() = runTest {
        val store = AccountActivityStore(MutableStateFlow(session), backgroundScope)
        val requests = mutableListOf<String>()
        val retrofit = fixtureRetrofit(requests) { sidebar + if(it.startsWith("/t/")) topic else list }
        val source = TopicRemoteDataSource(retrofit.create(V2exRawApi::class.java), Json, parser, activityStore = store)
        source.homeTopics(1)
        assertThat(store.activityPercent.first()).isEqualTo(59f)
        store.update(store.beginRequest(), 0f)
        source.homeTopics(2)
        assertThat(store.activityPercent.first()).isEqualTo(59f)
        store.update(store.beginRequest(), 0f)
        source.favoriteTopics(1)
        assertThat(store.activityPercent.first()).isEqualTo(59f)
        store.update(store.beginRequest(), 0f)
        assertThat(source.topicDetailPage(42, 1, 0)).isNotNull()
        assertThat(store.activityPercent.first()).isEqualTo(59f)
        store.update(store.beginRequest(), 0f)
        NodeRemoteDataSource(retrofit.create(V2exRawApi::class.java), retrofit.create(V2exWriteApi::class.java), Json, parser, activityStore = store)
            .topics("android", 1)
        assertThat(store.activityPercent.first()).isEqualTo(59f)
        assertThat(requests).containsExactly("/?tab=all", "/recent?p=1", "/my/topics?p=1", "/t/42", "/go/android").inOrder()
    }

    @Test
    fun unknownChallengedAnonymousAndWrongDestinationDoNotOverwriteKnownValue() = runTest {
        val store = AccountActivityStore(MutableStateFlow(session), backgroundScope)
        store.update(store.beginRequest(), 60f)
        for (bad in listOf(
            list, sidebar.replace("/my/nodes", "/signin") + list,
            "<html><head><title>Just a moment...</title></head><body>$sidebar$list</body></html>",
        )) {
            val retrofit = fixtureRetrofit(mutableListOf()) { bad }
            runCatching { TopicRemoteDataSource(retrofit.create(V2exRawApi::class.java), Json, parser, activityStore = store).homeTopics(1) }
            assertThat(store.activityPercent.first()).isEqualTo(60f)
        }
        for (destination in listOf("https://example.com/", "https://www.v2ex.com/signin", "https://www.v2ex.com/t/999")) {
            val retrofit = fixtureRetrofit(mutableListOf(), destination) { sidebar + topic }
            runCatching { TopicRemoteDataSource(retrofit.create(V2exRawApi::class.java), Json, parser, activityStore = store).topicDetailPage(42, 1, 0) }
            assertThat(store.activityPercent.first()).isEqualTo(60f)
        }
        val cached = fixtureRetrofit(mutableListOf(), cached = true) { sidebar + topic }
        TopicRemoteDataSource(cached.create(V2exRawApi::class.java), Json, parser, activityStore = store).topicDetailPage(42, 1, 0)
        assertThat(store.activityPercent.first()).isEqualTo(60f)
    }

    @Test
    fun accountRefreshPublishesDesktopHomeWithoutSeparateActivityRequest() = runTest {
        val store = AccountActivityStore(MutableStateFlow(session), backgroundScope)
        val requests = mutableListOf<String>()
        var desktop = false
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests += request.url.encodedPath
            if(request.url.encodedPath == "/") desktop = request.header("User-Agent")?.contains("Windows") == true
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(sidebar.toResponseBody()).build()
        }.build()
        val api = Retrofit.Builder().baseUrl("https://www.v2ex.com/").client(client).build().create(V2exRawApi::class.java)
        AccountRemoteDataSource(api, parser, activityStore = store).overview()
        assertThat(store.activityPercent.first()).isEqualTo(59f)
        assertThat(desktop).isTrue()
        assertThat(requests).containsExactly("/", "/mission/daily", "/balance").inOrder()
    }

    private fun fixtureRetrofit(requests: MutableList<String>, finalUrl: String? = null, cached: Boolean = false, html: (String) -> String): Retrofit {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath + request.url.encodedQuery?.let { "?$it" }.orEmpty()
            requests += path
            val raw = Response.Builder().request(finalUrl?.let { request.newBuilder().url(it).build() } ?: request)
                .protocol(Protocol.HTTP_1_1).code(200).message("OK")
            if(cached) raw.cacheResponse(Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").build())
            raw.body(html(request.url.encodedPath).toResponseBody()).build()
        }.build()
        return Retrofit.Builder().baseUrl("https://www.v2ex.com/").client(client).build()
    }
}
