package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.domain.reply.CreateReplyResult
import app.mystery0.nodeflow.domain.reply.ReplyFailureReason
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class ReplyRemoteDataSourceTest {
    private lateinit var server: MockWebServer
    private lateinit var dataSource: ReplyRemoteDataSource

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient.Builder().retryOnConnectionFailure(false).build())
            .build()
            .create(V2exWriteApi::class.java)
        dataSource = ReplyRemoteDataSource(api, V2exHtmlParser(), server.url("/"))
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun createReply_postsDynamicFormOnceAndReturnsSuccess() = runTest {
        server.enqueue(htmlResponse(topicPage(replyRows = oldReply, includeForm = true)))
        server.enqueue(htmlResponse(topicPage(replyRows = oldReply + newReply, includeForm = false)))

        val result = dataSource.createReply(42, "hello world")

        assertThat(result).isEqualTo(CreateReplyResult.Success(2))
        val get = server.takeRequest()
        val post = server.takeRequest()
        assertThat(get.method).isEqualTo("GET")
        assertThat(post.method).isEqualTo("POST")
        assertThat(post.path).isEqualTo("/t/42")
        val body = post.body.readUtf8()
        assertThat(body).contains("once=redacted")
        assertThat(body).contains("return_to_page=1")
        assertThat(body).contains("content=hello+world")
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun createReply_succeedsWhenPostReturnsUnparseableSuccessfulResponse() = runTest {
        server.enqueue(htmlResponse(topicPage(replyRows = oldReply, includeForm = true)))
        server.enqueue(htmlResponse("unexpected response"))

        val result = dataSource.createReply(42, "hello world")

        assertThat(result).isEqualTo(CreateReplyResult.Success(2))
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun createReply_readsLastPageBeforeSubmitAndDoesNotReadAfterSubmit() = runTest {
        server.enqueue(htmlResponse(topicPage(oldReply, includeForm = true, pageCount = 2)))
        server.enqueue(htmlResponse(topicPage(secondPageOldReply, includeForm = false, pageCount = 2)))
        server.enqueue(htmlResponse(topicPage(oldReply, includeForm = false, pageCount = 2)))

        val result = dataSource.createReply(42, "hello world")

        assertThat(result).isEqualTo(CreateReplyResult.Success(43))
        val requests = List(3) { server.takeRequest() }
        assertThat(requests.map { it.method }).containsExactly("GET", "GET", "POST").inOrder()
        assertThat(requests[1].path).isEqualTo("/t/42?p=2")
        assertThat(server.requestCount).isEqualTo(3)
    }

    @Test
    fun createReply_doesNotRepeatPostWhenServerReturnsTemporaryRedirect() = runTest {
        server.enqueue(htmlResponse(topicPage(replyRows = oldReply, includeForm = true)))
        server.enqueue(
            MockResponse()
                .setResponseCode(307)
                .addHeader("Location", server.url("redirected")),
        )
        server.enqueue(htmlResponse(topicPage(replyRows = oldReply + newReply, includeForm = false)))

        val result = dataSource.createReply(42, "hello world")

        assertThat(result).isInstanceOf(CreateReplyResult.Failure::class.java)
        result as CreateReplyResult.Failure
        assertThat(result.reason).isEqualTo(ReplyFailureReason.Server)
        assertThat(server.requestCount).isEqualTo(2)
        assertThat(server.takeRequest().method).isEqualTo("GET")
        assertThat(server.takeRequest().method).isEqualTo("POST")
    }

    @Test
    fun createReply_doesNotPostWhenReplyBaselineCannotBeParsed() = runTest {
        server.enqueue(htmlResponse("<html><body>${replyForm()}</body></html>"))
        server.enqueue(htmlResponse(topicPage(replyRows = newReply, includeForm = false)))

        val result = dataSource.createReply(42, "hello world")

        assertThat(result).isInstanceOf(CreateReplyResult.Failure::class.java)
        assertThat(server.requestCount).isEqualTo(1)
        assertThat(server.takeRequest().method).isEqualTo("GET")
    }

    @Test
    fun createReply_postsOnceForTitleOnlyTopicWithoutExistingReplies() = runTest {
        server.enqueue(htmlResponse(titleOnlyTopicPage(replyRows = "", includeForm = true)))
        server.enqueue(htmlResponse(titleOnlyTopicPage(replyRows = firstReply, includeForm = false)))

        val result = dataSource.createReply(42, "hello world")

        assertThat(result).isEqualTo(CreateReplyResult.Success(1))
        val requests = List(2) { server.takeRequest() }
        assertThat(requests.map { it.method }).containsExactly("GET", "POST").inOrder()
        assertThat(server.requestCount).isEqualTo(2)
    }

    private fun htmlResponse(body: String) = MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "text/html; charset=utf-8")
        .setBody(body)

    private fun topicPage(replyRows: String, includeForm: Boolean, pageCount: Int = 1) = """
        <html><body>
          <div class="topic_content">topic</div>
          $replyRows
          <input class="page_input" type="number" max="$pageCount" />
          ${if (includeForm) replyForm() else ""}
        </body></html>
    """.trimIndent()

    private fun titleOnlyTopicPage(replyRows: String, includeForm: Boolean) = """
        <html><body>
          <div class="header">
            <a href="/go/programmer">程序员</a>
            <h1>只有标题的主题</h1>
            <small class="gray"><a href="/member/author">author</a></small>
          </div>
          $replyRows
          ${if (includeForm) replyForm() else ""}
        </body></html>
    """.trimIndent()

    private fun replyForm() = """
        <form method="post" action="${server.url("t/42")}">
          <textarea name="content" maxlength="10000"></textarea>
          <input type="hidden" name="once" value="redacted" />
          <input type="hidden" name="return_to_page" value="1" />
        </form>
    """.trimIndent()

    private val oldReply: String
        get() = """
        <div id="r_100"><span class="no">1</span><strong><a href="/member/old">old</a></strong><div class="reply_content">old</div></div>
    """.trimIndent()

    private val newReply: String
        get() = """
        <div id="r_101"><span class="no">2</span><strong><a href="/member/tester">tester</a></strong><div class="reply_content">hello world</div></div>
    """.trimIndent()

    private val firstReply: String
        get() = """
        <div id="r_101"><span class="no">1</span><strong><a href="/member/tester">tester</a></strong><div class="reply_content">hello world</div></div>
    """.trimIndent()

    private val secondPageOldReply: String
        get() = """
        <div id="r_200"><span class="no">42</span><strong><a href="/member/second">second</a></strong><div class="reply_content">second old</div></div>
    """.trimIndent()
}
