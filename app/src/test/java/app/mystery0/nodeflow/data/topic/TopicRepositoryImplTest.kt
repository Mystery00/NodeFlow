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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
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

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun topicDetail_clearsCachedDetailAfterAccessDeniedBeforeLaterNetworkFailure() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = AccessDeniedThenFailingV2exRawApi()
        val repository = repository(api, dao, dispatcher)
        val localDataSource = TopicLocalDataSource(dao)
        localDataSource.cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1221181, replyCount = 0),
                content = "旧缓存正文",
                contentRendered = "<p>旧缓存正文</p>",
                replies = emptyList(),
            ),
        )

        val accessDeniedResult = repository.topicDetail(
            topicId = 1221181,
            forceRefresh = false,
        )
        val networkFailureResult = repository.topicDetail(
            topicId = 1221181,
            forceRefresh = false,
        )

        assertThat(accessDeniedResult.isFailure).isTrue()
        val accessDeniedError = accessDeniedResult.exceptionOrNull() as NodeFlowException
        assertThat(accessDeniedError.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(networkFailureResult.isFailure).isTrue()
        assertThat(api.topicHtmlCalls).isEqualTo(2)
        val cachedTopic = localDataSource.topic(1221181)
        assertThat(cachedTopic).isNotNull()
        assertThat(cachedTopic?.title).isEqualTo("标题 1221181")
        assertThat(cachedTopic?.node?.name).isEqualTo("python")
        assertThat(cachedTopic?.author?.username).isEqualTo("author")
        assertThat(cachedTopic?.replyCount).isEqualTo(0)
        assertThat(localDataSource.topicDetail(1221181)).isNull()
    }

    @Test
    fun topicDetail_concurrentFailureDoesNotUseCapturedCacheAfterAccessDenied() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = ConcurrentAccessDeniedV2exRawApi()
        val repository = repository(api, dao, dispatcher)
        TopicLocalDataSource(dao).cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1221181, replyCount = 0),
                content = "旧缓存正文",
                contentRendered = "<p>旧缓存正文</p>",
                replies = emptyList(),
            ),
        )

        val ordinaryFailure = async {
            repository.topicDetail(topicId = 1221181, forceRefresh = false)
        }
        runCurrent()
        assertThat(api.firstRequestStarted.isCompleted).isTrue()

        val accessDenied = async {
            repository.topicDetail(topicId = 1221181, forceRefresh = false)
        }
        runCurrent()
        val accessDeniedResult = accessDenied.await()

        api.releaseFirstFailure.complete(Unit)
        runCurrent()
        val ordinaryFailureResult = ordinaryFailure.await()

        assertThat(accessDeniedResult.isFailure).isTrue()
        assertThat((accessDeniedResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(ordinaryFailureResult.isFailure).isTrue()
        assertThat((ordinaryFailureResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun topicDetail_preservesAccessDeniedWhenClearingCacheFails() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val clearFailure = IllegalStateException("clear failed")
        val dao = FakeTopicDao(clearTopicDetailFailure = clearFailure)
        val api = AccessDeniedThenFailingV2exRawApi()
        val repository = repository(api, dao, dispatcher)
        val localDataSource = TopicLocalDataSource(dao)
        localDataSource.cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1221181, replyCount = 0),
                content = "旧缓存正文",
                contentRendered = "<p>旧缓存正文</p>",
                replies = emptyList(),
            ),
        )
        localDataSource.cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 2, replyCount = 0),
                content = "其他主题缓存",
                contentRendered = "<p>其他主题缓存</p>",
                replies = emptyList(),
            ),
        )

        val accessDeniedResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        val laterFailureResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        val otherTopicResult = repository.topicDetail(topicId = 2, forceRefresh = false)

        assertThat(accessDeniedResult.isFailure).isTrue()
        val accessDeniedError = accessDeniedResult.exceptionOrNull() as NodeFlowException
        assertThat(accessDeniedError.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(accessDeniedError.suppressed.asList()).containsExactly(clearFailure)
        assertThat(localDataSource.topicDetail(1221181)?.contentRendered)
            .isEqualTo("<p>旧缓存正文</p>")
        assertThat(laterFailureResult.isFailure).isTrue()
        assertThat((laterFailureResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(otherTopicResult.getOrThrow().contentRendered)
            .isEqualTo("<p>其他主题缓存</p>")
    }

    @Test
    fun topicDetail_successfulRefreshClearsAccessDeniedForLaterCacheFallback() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = AccessDeniedThenSuccessThenFailingV2exRawApi()
        val repository = repository(api, dao, dispatcher)

        val accessDeniedResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        val successfulResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        val laterFailureResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)

        assertThat(accessDeniedResult.isFailure).isTrue()
        assertThat((accessDeniedResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(successfulResult.getOrThrow().contentRendered)
            .isEqualTo("<p>新缓存正文</p>")
        assertThat(laterFailureResult.getOrThrow().contentRendered)
            .isEqualTo("<p>新缓存正文</p>")
    }

    @Test
    fun topicDetail_doesNotLetOlderSuccessClearNewerAccessDenied() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = ConcurrentSuccessThenAccessDeniedV2exRawApi()
        val repository = repository(api, dao, dispatcher)

        val olderSuccess = async {
            repository.topicDetail(topicId = 1221181, forceRefresh = false)
        }
        runCurrent()
        assertThat(api.firstRequestStarted.isCompleted).isTrue()

        val accessDenied = async {
            repository.topicDetail(topicId = 1221181, forceRefresh = false)
        }
        runCurrent()
        val accessDeniedResult = accessDenied.await()

        api.releaseFirstSuccess.complete(Unit)
        runCurrent()
        val olderSuccessResult = olderSuccess.await()

        assertThat((accessDeniedResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(olderSuccessResult.isFailure).isTrue()
        assertThat((olderSuccessResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(TopicLocalDataSource(dao).topicDetail(1221181)).isNull()
    }

    @Test
    fun topicDetail_doesNotLetOlderSuccessOverwriteRecoveryAfterAccessDenied() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = OlderSuccessAccessDeniedThenSuccessV2exRawApi()
        val repository = repository(api, dao, dispatcher)

        val olderSuccess = async {
            runCatching { repository.topicDetail(topicId = 1221181, forceRefresh = false) }
        }
        runCurrent()
        assertThat(api.firstRequestStarted.isCompleted).isTrue()

        val accessDeniedResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        val recoveredResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        api.releaseFirstSuccess.complete(Unit)
        runCurrent()
        val olderInvocation = olderSuccess.await()

        assertThat((accessDeniedResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(recoveredResult.getOrThrow().contentRendered).contains("最新正文")
        assertThat(olderInvocation.isFailure).isTrue()
        assertThat(olderInvocation.exceptionOrNull())
            .isInstanceOf(CancellationException::class.java)
        assertThat(TopicLocalDataSource(dao).topicDetail(1221181)?.contentRendered)
            .contains("最新正文")
    }

    @Test
    fun topicDetail_doesNotLetOlderFailureRestoreCacheAfterAccessDeniedRecovery() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = OlderFailureAccessDeniedThenSuccessV2exRawApi()
        val repository = repository(api, dao, dispatcher)
        TopicLocalDataSource(dao).cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1221181, replyCount = 0),
                content = "旧缓存正文",
                contentRendered = "<p>旧缓存正文</p>",
                replies = emptyList(),
            ),
        )

        val olderFailure = async {
            runCatching { repository.topicDetail(topicId = 1221181, forceRefresh = false) }
        }
        runCurrent()
        assertThat(api.firstRequestStarted.isCompleted).isTrue()

        val accessDeniedResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        val recoveredResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        api.releaseFirstFailure.complete(Unit)
        runCurrent()
        val olderInvocation = olderFailure.await()

        assertThat((accessDeniedResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(recoveredResult.getOrThrow().contentRendered).contains("最新正文")
        assertThat(olderInvocation.isFailure).isTrue()
        assertThat(olderInvocation.exceptionOrNull())
            .isInstanceOf(CancellationException::class.java)
        assertThat(TopicLocalDataSource(dao).topicDetail(1221181)?.contentRendered)
            .contains("最新正文")
    }

    @Test
    fun topicDetail_propagatesRemoteCancellation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellation = CancellationException("remote cancelled")
        val repository = repository(CancellingV2exRawApi(cancellation), FakeTopicDao(), dispatcher)

        val invocation = runCatching {
            repository.topicDetail(topicId = 1, forceRefresh = false)
        }

        assertThat(invocation.isFailure).isTrue()
        assertThat(invocation.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(invocation.exceptionOrNull()?.message).isEqualTo("remote cancelled")
    }

    @Test
    fun topicDetail_propagatesCacheClearCancellation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellation = CancellationException("clear cancelled")
        val dao = FakeTopicDao(clearTopicDetailFailure = cancellation)
        val repository = repository(AccessDeniedV2exRawApi(), dao, dispatcher)
        TopicLocalDataSource(dao).cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1221181, replyCount = 0),
                content = "旧缓存正文",
                contentRendered = "<p>旧缓存正文</p>",
                replies = emptyList(),
            ),
        )

        val invocation = runCatching {
            repository.topicDetail(topicId = 1221181, forceRefresh = false)
        }

        assertThat(invocation.isFailure).isTrue()
        assertThat(invocation.exceptionOrNull()).isInstanceOf(CancellationException::class.java)
        assertThat(invocation.exceptionOrNull()?.message).isEqualTo("clear cancelled")
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

    private class FakeTopicDao(
        private val clearTopicDetailFailure: Throwable? = null,
    ) : TopicDao {
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

        override suspend fun clearTopicDetail(topicId: Long) {
            clearTopicDetailFailure?.let { throw it }
            topics[topicId]?.let { topic ->
                topics[topicId] = topic.copy(
                    content = null,
                    contentRendered = null,
                )
            }
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

        override suspend fun redeemDailyMission(once: String, referer: String): Response<ResponseBody> = failure()

        override suspend fun balance(): Response<ResponseBody> = failure()

        override suspend fun notifications(page: Int): Response<ResponseBody> = failure()

        override suspend fun notesHtml(): Response<ResponseBody> = failure()

        override suspend fun noteEditHtml(id: Long): Response<ResponseBody> = failure()
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

    private class AccessDeniedThenFailingV2exRawApi : FailingV2exRawApi() {
        var topicHtmlCalls: Int = 0
            private set

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            if (topicHtmlCalls > 1) return super.topicHtml(topicId, page)

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

    private class ConcurrentAccessDeniedV2exRawApi : FailingV2exRawApi() {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstFailure = CompletableDeferred<Unit>()
        private var topicHtmlCalls = 0

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            if (topicHtmlCalls == 1) {
                firstRequestStarted.complete(Unit)
                releaseFirstFailure.await()
                return super.topicHtml(topicId, page)
            }
            return accessDeniedHtmlResponse()
        }
    }

    private class ConcurrentSuccessThenAccessDeniedV2exRawApi : FailingV2exRawApi() {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstSuccess = CompletableDeferred<Unit>()
        private var topicHtmlCalls = 0

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            if (topicHtmlCalls > 1) return accessDeniedHtmlResponse()

            firstRequestStarted.complete(Unit)
            releaseFirstSuccess.await()
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url("https://www.v2ex.com/t/$topicId").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                """
                    <html>
                      <body>
                        <div id="Main">
                          <h1>较早成功响应</h1>
                          <div class="topic_content"><p>较早正文</p></div>
                        </div>
                      </body>
                    </html>
                """.trimIndent().toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }
    }

    private class OlderSuccessAccessDeniedThenSuccessV2exRawApi : FailingV2exRawApi() {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstSuccess = CompletableDeferred<Unit>()
        private var topicHtmlCalls = 0

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            return when (topicHtmlCalls) {
                1 -> {
                    firstRequestStarted.complete(Unit)
                    releaseFirstSuccess.await()
                    topicHtmlResponse(topicId, "较早正文")
                }
                2 -> accessDeniedHtmlResponse()
                else -> topicHtmlResponse(topicId, "最新正文")
            }
        }

        private fun topicHtmlResponse(
            topicId: Long,
            content: String,
        ): Response<ResponseBody> {
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url("https://www.v2ex.com/t/$topicId").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                """
                    <html>
                      <body>
                        <div id="Main">
                          <h1>并发恢复主题</h1>
                          <div class="topic_content"><p>$content</p></div>
                        </div>
                      </body>
                    </html>
                """.trimIndent().toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }
    }

    private class OlderFailureAccessDeniedThenSuccessV2exRawApi : FailingV2exRawApi() {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstFailure = CompletableDeferred<Unit>()
        private var topicHtmlCalls = 0

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            return when (topicHtmlCalls) {
                1 -> {
                    firstRequestStarted.complete(Unit)
                    releaseFirstFailure.await()
                    super.topicHtml(topicId, page)
                }
                2 -> accessDeniedHtmlResponse()
                else -> topicHtmlResponse(topicId)
            }
        }

        private fun topicHtmlResponse(topicId: Long): Response<ResponseBody> {
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url("https://www.v2ex.com/t/$topicId").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                """
                    <html>
                      <body>
                        <div id="Main">
                          <h1>并发恢复主题</h1>
                          <div class="topic_content"><p>最新正文</p></div>
                        </div>
                      </body>
                    </html>
                """.trimIndent().toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }
    }

    private class CancellingV2exRawApi(
        private val cancellation: CancellationException,
    ) : FailingV2exRawApi() {
        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> = throw cancellation
    }

    private class AccessDeniedThenSuccessThenFailingV2exRawApi : FailingV2exRawApi() {
        private var topicHtmlCalls = 0

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            return when (topicHtmlCalls) {
                1 -> accessDeniedHtmlResponse()
                2, 3 -> topicHtmlResponse(topicId)
                else -> super.topicHtml(topicId, page)
            }
        }

        override suspend fun topic(id: Long): Response<ResponseBody> =
            if (topicHtmlCalls in 2..3) {
                jsonResponse(
                    """
                        [{"id": $id, "title": "新标题", "content": "新缓存正文", "content_rendered": "<p>新缓存正文</p>", "replies": 0}]
                    """.trimIndent(),
                )
            } else {
                super.topic(id)
            }

        override suspend fun replies(topicId: Long): Response<ResponseBody> =
            if (topicHtmlCalls in 2..3) {
                jsonResponse("[]")
            } else {
                super.replies(topicId)
            }

        private fun topicHtmlResponse(topicId: Long): Response<ResponseBody> {
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url("https://www.v2ex.com/t/$topicId").build())
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

    private companion object {
        fun accessDeniedHtmlResponse(): Response<ResponseBody> {
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
