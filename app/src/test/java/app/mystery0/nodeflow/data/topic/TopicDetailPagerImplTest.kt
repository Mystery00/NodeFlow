package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.entity.TopicEntity
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import java.io.IOException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TopicDetailPagerImplTest {

    @Test
    fun loadFirst_returnsFirstPageOnly() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(1..100)),
            ),
        )
        val pager = pager(remote)

        val snapshot = pager.loadFirst().getOrThrow()

        assertThat(remote.pageRequests).containsExactly(1 to 0)
        assertThat(snapshot.hasMore).isTrue()
        assertThat(snapshot.loadedPageCount).isEqualTo(1)
        assertThat(snapshot.totalPageCount).isEqualTo(3)
        assertThat(snapshot.detail.topic.replyCount).isEqualTo(250)
        assertThat(snapshot.detail.replies).hasSize(100)
    }

    @Test
    fun loadFirst_secondCallReturnsCachedSnapshotWithoutFetch() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 2, replyCount = 150, replies = replies(1..100)),
            ),
        )
        val pager = pager(remote)

        pager.loadFirst().getOrThrow()
        val second = pager.loadFirst().getOrThrow()

        assertThat(remote.pageRequests).hasSize(1)
        assertThat(second.detail.replies).hasSize(100)
    }

    @Test
    fun loadNext_appendsPagesInOrder() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(1..100)),
                2 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(101..200)),
            ),
        )
        val pager = pager(remote)

        pager.loadFirst().getOrThrow()
        val snapshot = pager.loadNext().getOrThrow()

        assertThat(remote.pageRequests).containsExactly(1 to 0, 2 to 100).inOrder()
        assertThat(snapshot.loadedPageCount).isEqualTo(2)
        assertThat(snapshot.hasMore).isTrue()
        assertThat(snapshot.detail.replies.map { it.floor })
            .isEqualTo((1..200).toList())
    }

    @Test
    fun loadNext_afterLastPageIsNoOp() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 1, replyCount = 3, replies = replies(1..3)),
            ),
        )
        val pager = pager(remote)

        pager.loadFirst().getOrThrow()
        val snapshot = pager.loadNext().getOrThrow()

        assertThat(remote.pageRequests).hasSize(1)
        assertThat(snapshot.hasMore).isFalse()
        assertThat(snapshot.detail.replies).hasSize(3)
    }

    @Test
    fun loadUntilFloor_loadsAcrossPages() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 3, replyCount = 260, replies = replies(1..100)),
                2 to parsedPage(pageCount = 3, replyCount = 260, replies = replies(101..200)),
                3 to parsedPage(pageCount = 3, replyCount = 260, replies = replies(201..260)),
            ),
        )
        val pager = pager(remote)

        val snapshot = pager.loadUntilFloor(250).getOrThrow()

        assertThat(remote.pageRequests.map { it.first }).containsExactly(1, 2, 3).inOrder()
        assertThat(snapshot.loadedPageCount).isEqualTo(3)
        assertThat(snapshot.hasMore).isFalse()
        assertThat(snapshot.detail.replies.last().floor).isEqualTo(260)
    }

    @Test
    fun loadUntilFloor_stopsAtLastPageWhenFloorExceedsTotal() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 2, replyCount = 120, replies = replies(1..100)),
                2 to parsedPage(pageCount = 2, replyCount = 120, replies = replies(101..120)),
            ),
        )
        val pager = pager(remote)

        val snapshot = pager.loadUntilFloor(999).getOrThrow()

        assertThat(snapshot.loadedPageCount).isEqualTo(2)
        assertThat(snapshot.hasMore).isFalse()
        assertThat(snapshot.detail.replies).hasSize(120)
    }

    @Test
    fun loadUntilFloor_midFailureKeepsLoadedPrefix() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(1..100)),
                2 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(101..200)),
            ),
            failures = mutableMapOf(3 to IOException("page 3 failed")),
        )
        val pager = pager(remote)

        val result = pager.loadUntilFloor(250)
        val current = pager.loadFirst().getOrThrow()

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(current.loadedPageCount).isEqualTo(2)
        assertThat(current.detail.replies).hasSize(200)
        assertThat(current.hasMore).isTrue()
    }

    @Test
    fun loadUntilLastPage_loadsAllPages() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(1..100)),
                2 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(101..200)),
                3 to parsedPage(pageCount = 3, replyCount = 250, replies = replies(201..250)),
            ),
        )
        val pager = pager(remote)

        val snapshot = pager.loadUntilLastPage().getOrThrow()

        assertThat(snapshot.hasMore).isFalse()
        assertThat(snapshot.detail.replies).hasSize(250)
    }

    @Test
    fun refreshLoaded_refetchesAllLoadedPagesAtomically() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 2, replyCount = 150, replies = replies(1..100)),
                2 to parsedPage(pageCount = 2, replyCount = 150, replies = replies(101..150)),
            ),
        )
        val pager = pager(remote)
        pager.loadUntilLastPage().getOrThrow()
        remote.pages[2] = parsedPage(
            pageCount = 2,
            replyCount = 160,
            replies = replies(101..160, content = "刷新后内容"),
        )
        remote.pageRequests.clear()

        val snapshot = pager.refreshLoaded().getOrThrow()

        assertThat(remote.pageRequests.map { it.first }).containsExactly(1, 2).inOrder()
        assertThat(snapshot.detail.topic.replyCount).isEqualTo(160)
        assertThat(snapshot.detail.replies).hasSize(160)
        assertThat(snapshot.detail.replies.last().content).isEqualTo("刷新后内容")
    }

    @Test
    fun refreshLoaded_midFailureKeepsOldState() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 2, replyCount = 150, replies = replies(1..100)),
                2 to parsedPage(pageCount = 2, replyCount = 150, replies = replies(101..150)),
            ),
        )
        val pager = pager(remote)
        pager.loadUntilLastPage().getOrThrow()
        remote.failures[2] = IOException("refresh page 2 failed")

        val result = pager.refreshLoaded()
        val current = pager.loadFirst().getOrThrow()

        assertThat(result.isFailure).isTrue()
        assertThat(current.detail.replies).hasSize(150)
        assertThat(current.loadedPageCount).isEqualTo(2)
    }

    @Test
    fun loadFirst_fallsBackToJsonWhenHtmlIsNotTopic() = runTest {
        val jsonDetail = topicDetail(replyCount = 2, replies = replies(1..2))
        val remote = FakeRemote(
            pages = mutableMapOf(),
            jsonFallback = jsonDetail,
        )
        val pager = pager(remote)

        val snapshot = pager.loadFirst().getOrThrow()

        assertThat(remote.jsonCalls).isEqualTo(1)
        assertThat(snapshot.hasMore).isFalse()
        assertThat(snapshot.totalPageCount).isEqualTo(1)
        assertThat(snapshot.detail).isEqualTo(jsonDetail)
    }

    @Test
    fun accessDenied_clearsCacheAndPropagates() = runTest {
        val dao = FakeTopicDao()
        val localDataSource = TopicLocalDataSource(dao)
        localDataSource.cacheTopicDetail(
            topicDetail(replyCount = 0, replies = emptyList()),
        )
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 2, replyCount = 150, replies = replies(1..100)),
            ),
            failures = mutableMapOf(2 to accessDenied()),
        )
        val pager = pager(remote, localDataSource)

        pager.loadFirst().getOrThrow()
        val result = pager.loadNext()

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(localDataSource.topicDetail(TOPIC_ID)).isNull()

        // 拒绝后分页状态被清空，重新 loadFirst 需要重新抓取第 1 页
        val afterDenied = pager.loadFirst()
        assertThat(afterDenied.getOrThrow().loadedPageCount).isEqualTo(1)
        assertThat(remote.pageRequests.map { it.first }).containsExactly(1, 2, 1).inOrder()
    }

    @Test
    fun loadFirst_failureFallsBackToCachedDetailForTopicWithoutReplies() = runTest {
        val dao = FakeTopicDao()
        val localDataSource = TopicLocalDataSource(dao)
        localDataSource.cacheTopicDetail(
            topicDetail(replyCount = 0, replies = emptyList(), contentRendered = "<p>缓存正文</p>"),
        )
        val remote = FakeRemote(
            pages = mutableMapOf(),
            failures = mutableMapOf(1 to IOException("network down")),
            jsonFallbackFailure = IOException("json down"),
        )
        val pager = pager(remote, localDataSource)

        val snapshot = pager.loadFirst().getOrThrow()

        assertThat(snapshot.hasMore).isFalse()
        assertThat(snapshot.detail.contentRendered).isEqualTo("<p>缓存正文</p>")
    }

    @Test
    fun loadFirst_failureWithoutUsableCacheReturnsFailure() = runTest {
        val remote = FakeRemote(
            pages = mutableMapOf(),
            failures = mutableMapOf(1 to IOException("network down")),
            jsonFallbackFailure = IOException("json down"),
        )
        val pager = pager(remote)

        val result = pager.loadFirst()

        assertThat(result.isFailure).isTrue()
    }

    @Test
    fun crossPageReferencePreview_resolvesEarlierPageFloor() = runTest {
        val referencing = reply(
            floor = 101,
            author = "bob",
            content = "@user50 #50 同意你的观点",
        )
        val remote = FakeRemote(
            pages = mutableMapOf(
                1 to parsedPage(pageCount = 2, replyCount = 101, replies = replies(1..100)),
                2 to parsedPage(pageCount = 2, replyCount = 101, replies = listOf(referencing)),
            ),
        )
        val pager = pager(remote)

        val snapshot = pager.loadUntilLastPage().getOrThrow()

        val reference = snapshot.detail.replies.last().reference
        assertThat(reference).isNotNull()
        assertThat(reference?.floor).isEqualTo(50)
        assertThat(reference?.author?.username).isEqualTo("user50")
    }

    private fun kotlinx.coroutines.test.TestScope.pager(
        remote: FakeRemote,
        localDataSource: TopicLocalDataSource = TopicLocalDataSource(FakeTopicDao()),
    ): TopicDetailPagerImpl = TopicDetailPagerImpl(
        topicId = TOPIC_ID,
        fetchPage = remote::fetchPage,
        fetchJsonFallback = remote::fetchJson,
        localDataSource = localDataSource,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        accessState = TopicAccessState(),
    )

    private class FakeRemote(
        val pages: MutableMap<Int, V2exHtmlParser.ParsedTopicHtml>,
        val failures: MutableMap<Int, Throwable> = mutableMapOf(),
        private val jsonFallback: TopicDetail? = null,
        private val jsonFallbackFailure: Throwable? = null,
    ) {
        val pageRequests = mutableListOf<Pair<Int, Int>>()
        var jsonCalls = 0
            private set

        suspend fun fetchPage(page: Int, floorOffset: Int): V2exHtmlParser.ParsedTopicHtml? {
            pageRequests += page to floorOffset
            failures[page]?.let { throw it }
            return pages[page]
        }

        suspend fun fetchJson(): TopicDetail {
            jsonCalls += 1
            jsonFallbackFailure?.let { throw it }
            return checkNotNull(jsonFallback) { "json fallback not configured" }
        }
    }

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

        override suspend fun clearTopicDetail(topicId: Long) {
            topics[topicId]?.let { topic ->
                topics[topicId] = topic.copy(content = null, contentRendered = null)
            }
        }

        override suspend fun clear() {
            topics.clear()
        }
    }

    private companion object {
        const val TOPIC_ID = 1221181L

        fun parsedPage(
            pageCount: Int,
            replyCount: Int?,
            replies: List<Reply>,
        ): V2exHtmlParser.ParsedTopicHtml = V2exHtmlParser.ParsedTopicHtml(
            id = TOPIC_ID,
            title = "分页主题",
            authorName = "author",
            authorAvatarUrl = null,
            nodeName = "python",
            nodeTitle = "Python",
            contentRendered = "<p>正文</p>",
            replyCount = replyCount,
            pageCount = pageCount,
            replies = replies,
        )

        fun replies(range: IntRange, content: String = "回复内容"): List<Reply> =
            range.map { floor -> reply(floor = floor, author = "user$floor", content = content) }

        fun reply(floor: Int, author: String, content: String): Reply = Reply(
            id = floor.toLong(),
            topicId = TOPIC_ID,
            floor = floor,
            author = User(username = author),
            content = content,
            contentRendered = "<p>$content</p>",
        )

        fun topicDetail(
            replyCount: Int,
            replies: List<Reply>,
            contentRendered: String = "<p>正文</p>",
        ): TopicDetail = TopicDetail(
            topic = Topic(
                id = TOPIC_ID,
                title = "分页主题",
                url = "https://www.v2ex.com/t/$TOPIC_ID",
                node = Node(name = "python", title = "Python"),
                author = User(username = "author"),
                replyCount = replyCount,
            ),
            content = "",
            contentRendered = contentRendered,
            replies = replies,
        )

        fun accessDenied(): NodeFlowException = NodeFlowException(
            kind = NodeFlowException.Kind.AccessDenied,
            message = "access denied",
        )
    }
}
