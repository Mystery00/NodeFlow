package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.testing.FakeTopicDao
import app.mystery0.nodeflow.testing.TestV2exRawApi
import app.mystery0.nodeflow.testing.testHtmlResponse
import app.mystery0.nodeflow.testing.testJsonResponse
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
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class TopicRepositoryImplTest {
    @Test
    fun clearCacheResetsOldDenialAfterCacheWasSuccessfullyRemoved() = runTest {
        val dao = FakeTopicDao()
        val repo = repository(accessDeniedThenFailingApi(), dao, StandardTestDispatcher(testScheduler))
        val pager = repo.topicDetailPager(1221181)
        assertThat(pager.loadFirst().isFailure).isTrue()
        repo.clearCache()
        TopicLocalDataSource(dao).cacheTopicDetail(TopicDetail(
            topic = topic(1221181, replyCount = 0), content = "新缓存正文",
            contentRendered = "<p>新缓存正文</p>", replies = emptyList(),
        ))
        assertThat(pager.loadFirst().getOrThrow().detail.content).isEqualTo("新缓存正文")
    }
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

    /** 旧 topicDetail 语义现在由 pager 的首屏加载承载；每次调用创建新 pager，与旧的按次请求等价。 */
    private suspend fun TopicRepositoryImpl.topicDetail(
        topicId: Long,
        forceRefresh: Boolean,
    ): Result<TopicDetail> = topicDetailPager(topicId).loadFirst(forceRefresh).map { it.detail }

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
        val api = accessDeniedApi()
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
        val api = accessDeniedThenFailingApi()
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
        val api = concurrentAccessDeniedApi()
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

        api.releaseFirstRequest.complete(Unit)
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
        val api = accessDeniedThenFailingApi()
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
        val api = accessDeniedThenSuccessThenFailingApi()
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
        val api = concurrentSuccessThenAccessDeniedApi()
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

        api.releaseFirstRequest.complete(Unit)
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
        val api = olderSuccessAccessDeniedThenSuccessApi()
        val repository = repository(api, dao, dispatcher)

        val olderSuccess = async {
            runCatching { repository.topicDetail(topicId = 1221181, forceRefresh = false) }
        }
        runCurrent()
        assertThat(api.firstRequestStarted.isCompleted).isTrue()

        val accessDeniedResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        val recoveredResult = repository.topicDetail(topicId = 1221181, forceRefresh = false)
        api.releaseFirstRequest.complete(Unit)
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
        val api = olderFailureAccessDeniedThenSuccessApi()
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
        api.releaseFirstRequest.complete(Unit)
        runCurrent()
        val olderInvocation = olderFailure.await()

        assertThat((accessDeniedResult.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(recoveredResult.getOrThrow().contentRendered).contains("最新正文")
        // 分页实现的失败回退读取的是回退时刻的最新缓存，较早失败只能拿到恢复后的
        // “最新正文”，绝不会把拒绝前捕获的旧缓存重新变成结果
        assertThat(olderInvocation.getOrThrow().getOrThrow().contentRendered)
            .contains("最新正文")
        assertThat(TopicLocalDataSource(dao).topicDetail(1221181)?.contentRendered)
            .contains("最新正文")
    }

    @Test
    fun topicDetail_propagatesRemoteCancellation() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val cancellation = CancellationException("remote cancelled")
        val repository = repository(
            ScriptedTopicApi { _, _, _ -> throw cancellation },
            FakeTopicDao(),
            dispatcher,
        )

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
        val repository = repository(accessDeniedApi(), dao, dispatcher)
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
        val repository = repository(successTopicApi(), dao, dispatcher)

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

    private open class FailingV2exRawApi : TestV2exRawApi(
        fallback = { Response.error(500, "".toResponseBody("text/plain".toMediaType())) },
    )

    private class ScriptedTopicApi(
        private val onTopic: (suspend (topicId: Long, callCount: Int) -> Response<ResponseBody>?)? = null,
        private val onReplies: (suspend (topicId: Long, callCount: Int) -> Response<ResponseBody>?)? = null,
        private val onTopicHtml: suspend ScriptedTopicApi.(topicId: Long, page: Int?, callCount: Int) -> Response<ResponseBody>,
    ) : FailingV2exRawApi() {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstRequest = CompletableDeferred<Unit>()
        var topicHtmlCalls: Int = 0
            private set

        override suspend fun topic(id: Long): Response<ResponseBody> =
            onTopic?.invoke(id, topicHtmlCalls) ?: super.topic(id)

        override suspend fun replies(topicId: Long): Response<ResponseBody> =
            onReplies?.invoke(topicId, topicHtmlCalls) ?: super.replies(topicId)

        override suspend fun topicHtml(topicId: Long, page: Int?): Response<ResponseBody> {
            topicHtmlCalls += 1
            return onTopicHtml(topicId, page, topicHtmlCalls)
        }

        suspend fun failTopicHtml(topicId: Long, page: Int?): Response<ResponseBody> =
            super.topicHtml(topicId, page)
    }

    private fun accessDeniedApi(): ScriptedTopicApi =
        ScriptedTopicApi { _, _, _ -> accessDeniedHtmlResponse() }

    private fun accessDeniedThenFailingApi(): ScriptedTopicApi =
        ScriptedTopicApi { topicId, page, callCount ->
            if (callCount == 1) accessDeniedHtmlResponse() else failTopicHtml(topicId, page)
        }

    private fun concurrentAccessDeniedApi(): ScriptedTopicApi =
        ScriptedTopicApi { topicId, page, callCount ->
            if (callCount == 1) {
                firstRequestStarted.complete(Unit)
                releaseFirstRequest.await()
                failTopicHtml(topicId, page)
            } else {
                accessDeniedHtmlResponse()
            }
        }

    private fun concurrentSuccessThenAccessDeniedApi(): ScriptedTopicApi =
        ScriptedTopicApi { topicId, _, callCount ->
            if (callCount > 1) {
                accessDeniedHtmlResponse()
            } else {
                firstRequestStarted.complete(Unit)
                releaseFirstRequest.await()
                renderedTopicHtmlResponse(topicId, title = "较早成功响应", paragraph = "较早正文")
            }
        }

    private fun olderSuccessAccessDeniedThenSuccessApi(): ScriptedTopicApi =
        ScriptedTopicApi { topicId, _, callCount ->
            when (callCount) {
                1 -> {
                    firstRequestStarted.complete(Unit)
                    releaseFirstRequest.await()
                    renderedTopicHtmlResponse(topicId, title = "并发恢复主题", paragraph = "较早正文")
                }
                2 -> accessDeniedHtmlResponse()
                else -> renderedTopicHtmlResponse(topicId, title = "并发恢复主题", paragraph = "最新正文")
            }
        }

    private fun olderFailureAccessDeniedThenSuccessApi(): ScriptedTopicApi =
        ScriptedTopicApi { topicId, page, callCount ->
            when (callCount) {
                1 -> {
                    firstRequestStarted.complete(Unit)
                    releaseFirstRequest.await()
                    failTopicHtml(topicId, page)
                }
                2 -> accessDeniedHtmlResponse()
                else -> renderedTopicHtmlResponse(topicId, title = "并发恢复主题", paragraph = "最新正文")
            }
        }

    private fun accessDeniedThenSuccessThenFailingApi(): ScriptedTopicApi =
        ScriptedTopicApi(
            onTopic = { id, callCount ->
                if (callCount in 2..3) {
                    testJsonResponse(
                        """[{"id": $id, "title": "新标题", "content": "新缓存正文", "content_rendered": "<p>新缓存正文</p>", "replies": 0}]""",
                    )
                } else {
                    null
                }
            },
            onReplies = { _, callCount ->
                if (callCount in 2..3) testJsonResponse("[]") else null
            },
            onTopicHtml = { topicId, page, callCount ->
                when (callCount) {
                    1 -> accessDeniedHtmlResponse()
                    2, 3 -> testHtmlResponse("<html><body></body></html>", "https://www.v2ex.com/t/$topicId")
                    else -> failTopicHtml(topicId, page)
                }
            },
        )

    private fun successTopicApi(): ScriptedTopicApi =
        ScriptedTopicApi(
            onTopic = { id, _ ->
                testJsonResponse(
                    """[{"id": $id, "title": "远端标题", "content": "hello", "content_rendered": "<p>hello</p>", "replies": 1}]""",
                )
            },
            onReplies = { topicId, _ ->
                testJsonResponse(
                    """[{"id": 100, "topic_id": $topicId, "content": "reply", "content_rendered": "<p>reply</p>", "member": {"username": "livid"}}]""",
                )
            },
            onTopicHtml = { topicId, page, _ ->
                val finalUrl = "https://www.v2ex.com/t/$topicId" + page?.let { "?p=$it" }.orEmpty()
                testHtmlResponse("<html><body></body></html>", finalUrl)
            },
        )

    private fun accessDeniedHtmlResponse(): Response<ResponseBody> =
        testHtmlResponse("<html><body>Restricted</body></html>", "https://www.v2ex.com/restricted")

    private fun renderedTopicHtmlResponse(
        topicId: Long,
        title: String,
        paragraph: String,
    ): Response<ResponseBody> = testHtmlResponse(
        html = """
            <html>
              <body>
                <div id="Main">
                  <h1>$title</h1>
                  <div class="topic_content"><p>$paragraph</p></div>
                </div>
              </body>
            </html>
        """.trimIndent(),
        finalUrl = "https://www.v2ex.com/t/$topicId",
    )
}
