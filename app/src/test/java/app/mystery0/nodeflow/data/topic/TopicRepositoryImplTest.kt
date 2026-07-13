package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.entity.TopicEntity
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response as OkHttpResponse
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class TopicRepositoryImplTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun repository(
        api: V2exRawApi,
        dao: TopicDao,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): TopicRepositoryImpl = TopicRepositoryImpl(
        remoteDataSource = TopicRemoteDataSource(api, json, V2exHtmlParser()),
        localDataSource = TopicLocalDataSource(dao),
        ioDispatcher = dispatcher,
    )

    @Test
    fun topicDetail_failsWhenRemoteFailsAndCacheHasNoReplies() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val repository = repository(FailingV2exRawApi(), dao, dispatcher)
        // 列表缓存过该主题（带回复数），但从未成功抓取过详情
        TopicLocalDataSource(dao).cacheTopics(listOf(topic(id = 1, replyCount = 5)))

        val result = repository.topicDetail(topicId = 1, forceRefresh = false)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun topicDetail_failsWhenRemoteFailsAndCachedDetailMissesReplies() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val repository = repository(FailingV2exRawApi(), dao, dispatcher)
        // 详情缓存过正文，但缓存里没有回复，而主题声明有 5 条回复
        TopicLocalDataSource(dao).cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1, replyCount = 5),
                content = "正文",
                contentRendered = "<p>正文</p>",
                replies = emptyList(),
            ),
        )

        val result = repository.topicDetail(topicId = 1, forceRefresh = true)

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun topicDetail_fallsBackToCachedDetailWhenTopicHasNoReplies() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val repository = repository(FailingV2exRawApi(), dao, dispatcher)
        TopicLocalDataSource(dao).cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1, replyCount = 0),
                content = "正文",
                contentRendered = "<p>正文</p>",
                replies = emptyList(),
            ),
        )

        val result = repository.topicDetail(topicId = 1, forceRefresh = true)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.getOrThrow().contentRendered).isEqualTo("<p>正文</p>")
    }

    @Test
    fun topicDetail_doesNotReturnCachedDetailWhenRemoteReportsAccessDenied() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = AccessDeniedV2exRawApi()
        val repository = repository(api, dao, dispatcher)
        TopicLocalDataSource(dao).cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1221181, replyCount = 0),
                content = "旧缓存正文",
                contentRendered = "<p>旧缓存正文</p>",
                replies = emptyList(),
            ),
        )

        val result = repository.topicDetail(
            topicId = 1221181,
            forceRefresh = false,
        )

        assertThat(api.topicHtmlCalls).isEqualTo(1)
        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun topicDetail_returnsRemoteDetailWhenRemoteSucceeds() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val repository = repository(SuccessV2exRawApi(), dao, dispatcher)

        val result = repository.topicDetail(topicId = 42, forceRefresh = false)

        val detail = result.getOrThrow()
        assertThat(detail.topic.id).isEqualTo(42)
        assertThat(detail.contentRendered).isEqualTo("<p>hello</p>")
        assertThat(detail.replies).hasSize(1)
        assertThat(detail.replies.single().author.username).isEqualTo("livid")
    }

    private fun topic(id: Long, replyCount: Int): Topic = Topic(
        id = id,
        title = "标题 $id",
        url = "https://www.v2ex.com/t/$id",
        node = Node(name = "python", title = "Python"),
        author = User(username = "author"),
        replyCount = replyCount,
    )

    private class FakeTopicDao : TopicDao {
        private val topics = mutableMapOf<Long, TopicEntity>()

        override suspend fun latestTopics(limit: Int): List<TopicEntity> = topics.values.toList()

        override suspend fun topicsByNode(nodeName: String, limit: Int): List<TopicEntity> =
            topics.values.filter { it.nodeName == nodeName }

        override suspend fun topic(id: Long): TopicEntity? = topics[id]

        override suspend fun upsertTopics(topics: List<TopicEntity>) {
            topics.forEach { this.topics[it.id] = it }
        }

        override suspend fun upsertTopic(topic: TopicEntity) {
            topics[topic.id] = topic
        }

        override suspend fun clear() {
            topics.clear()
        }
    }

    private open class FailingV2exRawApi : V2exRawApi {
        private fun failure(): Response<ResponseBody> =
            Response.error(500, "".toResponseBody("text/plain".toMediaType()))

        override suspend fun latestTopics(): Response<ResponseBody> = failure()

        override suspend fun topic(id: Long): Response<ResponseBody> = failure()

        override suspend fun replies(topicId: Long): Response<ResponseBody> = failure()

        override suspend fun node(name: String): Response<ResponseBody> = failure()

        override suspend fun member(username: String): Response<ResponseBody> = failure()

        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?): Response<ResponseBody> = failure()

        override suspend fun recentTopicsHtml(page: Int?): Response<ResponseBody> = failure()

        override suspend fun planesHtml(): Response<ResponseBody> = failure()

        override suspend fun topicHtml(topicId: Long, page: Int?): Response<ResponseBody> = failure()

        override suspend fun memberHtml(username: String): Response<ResponseBody> = failure()

        override suspend fun signInPage(next: String): Response<ResponseBody> = failure()

        override suspend fun captcha(cacheBust: Long, referer: String): Response<ResponseBody> = failure()

        override suspend fun signIn(
            fields: Map<String, String>,
            origin: String,
            referer: String,
        ): Response<ResponseBody> = failure()

        override suspend fun signInTwoFactor(
            next: String,
            fields: Map<String, String>,
            referer: String,
        ): Response<ResponseBody> = failure()

        override suspend fun home(): Response<ResponseBody> = failure()

        override suspend fun dailyMission(): Response<ResponseBody> = failure()

        override suspend fun balance(): Response<ResponseBody> = failure()
    }

    private class AccessDeniedV2exRawApi : FailingV2exRawApi() {
        var topicHtmlCalls: Int = 0
            private set

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            val rawResponse = OkHttpResponse.Builder()
                .request(
                    Request.Builder()
                        .url("https://www.v2ex.com/restricted")
                        .build(),
                )
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                "<html><body>Restricted</body></html>"
                    .toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }
    }

    private class SuccessV2exRawApi : FailingV2exRawApi() {
        override suspend fun topic(id: Long): Response<ResponseBody> = jsonResponse(
            """
                [{"id": $id, "title": "远端标题", "content": "hello", "content_rendered": "<p>hello</p>", "replies": 1}]
            """.trimIndent(),
        )

        override suspend fun replies(topicId: Long): Response<ResponseBody> = jsonResponse(
            """
                [{"id": 100, "topic_id": $topicId, "content": "reply", "content_rendered": "<p>reply</p>", "member": {"username": "livid"}}]
            """.trimIndent(),
        )

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            val finalUrl = "https://www.v2ex.com/t/$topicId" +
                page?.let { "?p=$it" }.orEmpty()
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url(finalUrl).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                "<html><body></body></html>"
                    .toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }

        private fun jsonResponse(body: String): Response<ResponseBody> =
            Response.success(body.toResponseBody("application/json".toMediaType()))
    }
}
