package app.mystery0.nodeflow.data.node

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.entity.NodeEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneNodeEntity
import app.mystery0.nodeflow.core.database.entity.TopicEntity
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.data.topic.TopicLocalDataSource
import com.google.common.truth.Truth.assertThat
import java.io.IOException
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

class NodeRepositoryImplTest {
    @Test
    fun topics_doesNotFallBackToCacheWhenRemoteReportsAccessDenied() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val topicDao = FakeTopicDao()
        val topicLocalDataSource = TopicLocalDataSource(topicDao)
        topicLocalDataSource.cacheTopics(listOf(cachedTopic()))
        val api = NodeTopicsV2exRawApi(accessDenied = true)
        val repository = NodeRepositoryImpl(
            remoteDataSource = NodeRemoteDataSource(
                api = api,
                json = Json { ignoreUnknownKeys = true },
                parser = V2exHtmlParser(),
            ),
            localDataSource = NodeLocalDataSource(FakeNodeDao()),
            topicLocalDataSource = topicLocalDataSource,
            ioDispatcher = dispatcher,
        )

        val result = repository.topics(
            name = "flamewar",
            page = 1,
            forceRefresh = true,
        )

        assertThat(api.nodeTopicsHtmlCalls).isEqualTo(1)
        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun topics_fallsBackToCacheWhenRemoteFailsNormally() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val topicDao = FakeTopicDao()
        val topicLocalDataSource = TopicLocalDataSource(topicDao)
        topicLocalDataSource.cacheTopics(listOf(cachedTopic()))
        val api = NodeTopicsV2exRawApi(accessDenied = false)
        val repository = NodeRepositoryImpl(
            remoteDataSource = NodeRemoteDataSource(
                api = api,
                json = Json { ignoreUnknownKeys = true },
                parser = V2exHtmlParser(),
            ),
            localDataSource = NodeLocalDataSource(FakeNodeDao()),
            topicLocalDataSource = topicLocalDataSource,
            ioDispatcher = dispatcher,
        )

        val result = repository.topics(
            name = "flamewar",
            page = 1,
            forceRefresh = true,
        )

        assertThat(api.nodeTopicsHtmlCalls).isEqualTo(1)
        assertThat(result.getOrThrow().map { topic -> topic.id }).containsExactly(1221181L)
    }

    private fun cachedTopic(): Topic = Topic(
        id = 1221181,
        title = "旧缓存主题",
        url = "https://www.v2ex.com/t/1221181",
        node = Node(name = "flamewar", title = "水深火热"),
        author = User(username = "alice"),
    )

    private class FakeTopicDao : TopicDao {
        private val topics = mutableMapOf<Long, TopicEntity>()

        override suspend fun latestTopics(limit: Int): List<TopicEntity> =
            topics.values.toList()

        override suspend fun topicsByNode(nodeName: String, limit: Int): List<TopicEntity> =
            topics.values.filter { it.nodeName == nodeName }

        override suspend fun topic(id: Long): TopicEntity? = topics[id]

        override suspend fun upsertTopics(topics: List<TopicEntity>) {
            topics.forEach { topic -> this.topics[topic.id] = topic }
        }

        override suspend fun upsertTopic(topic: TopicEntity) {
            topics[topic.id] = topic
        }

        override suspend fun clearTopicDetail(topicId: Long) = Unit

        override suspend fun clear() {
            topics.clear()
        }
    }

    private class FakeNodeDao : NodeDao {
        override suspend fun node(name: String): NodeEntity? = null

        override suspend fun nodePlanes(): List<NodePlaneEntity> = emptyList()

        override suspend fun nodesInPlane(planeName: String): List<NodeEntity> = emptyList()

        override suspend fun upsertNode(node: NodeEntity) = Unit

        override suspend fun upsertNodes(nodes: List<NodeEntity>) = Unit

        override suspend fun upsertNodePlanes(planes: List<NodePlaneEntity>) = Unit

        override suspend fun insertNodePlaneNodes(nodes: List<NodePlaneNodeEntity>) = Unit

        override suspend fun clearNodePlaneNodes() = Unit

        override suspend fun clearNodePlanes() = Unit

        override suspend fun clear() = Unit
    }

    private class NodeTopicsV2exRawApi(
        private val accessDenied: Boolean,
    ) : V2exRawApi {
        var nodeTopicsHtmlCalls: Int = 0
            private set

        private fun failure(): Response<ResponseBody> =
            Response.error(500, "".toResponseBody("text/plain".toMediaType()))

        override suspend fun latestTopics(): Response<ResponseBody> = failure()

        override suspend fun topic(id: Long): Response<ResponseBody> = failure()

        override suspend fun replies(topicId: Long): Response<ResponseBody> = failure()

        override suspend fun node(name: String): Response<ResponseBody> = failure()

        override suspend fun member(username: String): Response<ResponseBody> = failure()

        override suspend fun nodeTopicsHtml(
            nodeName: String,
            page: Int?,
        ): Response<ResponseBody> {
            nodeTopicsHtmlCalls += 1
            if (!accessDenied) throw IOException("offline")
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url("https://www.v2ex.com/").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                "<html><body>Home</body></html>"
                    .toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }

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
    }
}
