package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.isAccessDenied
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.domain.topic.TopicDetailPager
import app.mystery0.nodeflow.domain.topic.TopicDetailSnapshot
import java.util.TreeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * [TopicDetailPager] 的数据层实现。
 *
 * 分页状态（已加载页、页数、总回复数）全部封装在实例内部并由 [mutex] 串行化；
 * access-denied 的世代判定通过共享的 [TopicAccessState] 与同主题的其他请求方保持一致。
 */
internal class TopicDetailPagerImpl(
    private val topicId: Long,
    private val fetchPage: suspend (page: Int, floorOffset: Int) -> V2exHtmlParser.ParsedTopicHtml?,
    private val fetchJsonFallback: suspend () -> TopicDetail,
    private val localDataSource: TopicLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    private val accessState: TopicAccessState,
) : TopicDetailPager {
    private val mutex = Mutex()

    // 以下状态仅在持有 mutex 时读写
    private var header: V2exHtmlParser.ParsedTopicHtml? = null
    private var jsonDetail: TopicDetail? = null
    private val pages = TreeMap<Int, List<Reply>>()
    private var pageCount = 1
    private var totalReplyCount: Int? = null

    override suspend fun loadFirst(forceRefresh: Boolean): Result<TopicDetailSnapshot> =
        withContext(ioDispatcher) {
            try {
                mutex.withLock {
                    if (!forceRefresh && loadedLocked()) {
                        return@withContext Result.success(snapshotLocked())
                    }
                    resetLocked()
                    fetchFirstPageLocked()
                    Result.success(snapshotLocked())
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                firstLoadFallback(error)
            }
        }

    override suspend fun loadNext(): Result<TopicDetailSnapshot> = runOp {
        if (!loadedLocked()) {
            fetchFirstPageLocked()
        } else {
            loadNextPageLocked()
        }
        snapshotLocked()
    }

    override suspend fun loadUntilFloor(floor: Int): Result<TopicDetailSnapshot> = runOp {
        if (!loadedLocked()) fetchFirstPageLocked()
        while (jsonDetail == null && loadedReplyCountLocked() < floor && hasMoreLocked()) {
            loadNextPageLocked()
        }
        snapshotLocked()
    }

    override suspend fun loadUntilLastPage(): Result<TopicDetailSnapshot> = runOp {
        if (!loadedLocked()) fetchFirstPageLocked()
        while (hasMoreLocked()) {
            loadNextPageLocked()
        }
        snapshotLocked()
    }

    override suspend fun refreshLoaded(): Result<TopicDetailSnapshot> = runOp {
        if (header == null || jsonDetail != null) {
            // 未加载过或处于 JSON 兜底态：等价于强制重抓第一步
            resetLocked()
            fetchFirstPageLocked()
            return@runOp snapshotLocked()
        }
        val loadedPage = pages.lastKey()
        val freshPages = TreeMap<Int, List<Reply>>()
        var freshHeader: V2exHtmlParser.ParsedTopicHtml? = null
        var freshPageCount = 1
        var freshTotalReplyCount: Int? = null
        var page = 1
        var lastPageLimit = loadedPage
        while (page <= lastPageLimit) {
            val floorOffset = freshPages.values.sumOf { it.size }
            val parsed = fetchGuarded { fetchPage(page, floorOffset) }
            if (parsed == null) {
                if (page == 1) {
                    // 主题页不再可解析：退回首次加载流程（含 JSON 兜底）
                    resetLocked()
                    fetchFirstPageLocked()
                    return@runOp snapshotLocked()
                }
                // 页数缩减：保留已重拉的前缀
                break
            }
            freshPages[page] = parsed.replies
            if (page == 1) freshHeader = parsed
            parsed.replyCount?.let { freshTotalReplyCount = it }
            freshPageCount = parsed.pageCount
            lastPageLimit = minOf(lastPageLimit, parsed.pageCount)
            page += 1
        }
        // 全部页成功后才原子替换旧状态
        resetLocked()
        pages.putAll(freshPages)
        header = checkNotNull(freshHeader)
        pageCount = maxOf(freshPageCount, pages.lastKey())
        totalReplyCount = freshTotalReplyCount
        cacheHeaderLocked(checkNotNull(freshHeader))
        snapshotLocked()
    }

    private suspend fun runOp(block: suspend () -> TopicDetailSnapshot): Result<TopicDetailSnapshot> =
        withContext(ioDispatcher) {
            try {
                Result.success(mutex.withLock { block() })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Result.failure(error)
            }
        }

    private fun loadedLocked(): Boolean = header != null || jsonDetail != null

    private fun loadedPageLocked(): Int = if (pages.isEmpty()) 0 else pages.lastKey()

    private fun loadedReplyCountLocked(): Int = pages.values.sumOf { it.size }

    private fun hasMoreLocked(): Boolean = jsonDetail == null && loadedPageLocked() < pageCount

    private fun resetLocked() {
        header = null
        jsonDetail = null
        pages.clear()
        pageCount = 1
        totalReplyCount = null
    }

    private suspend fun fetchFirstPageLocked() {
        val parsed = try {
            fetchGuarded { fetchPage(1, 0) }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error.isAccessDenied()) throw error
            // 网页线路非权限失败时回退旧 JSON API
            null
        }
        if (parsed != null) {
            applyPageLocked(1, parsed)
        } else {
            val fallback = fetchGuarded { fetchJsonFallback() }
            jsonDetail = fallback
            localDataSource.cacheTopicDetail(fallback)
        }
    }

    private suspend fun loadNextPageLocked() {
        if (!hasMoreLocked()) return
        val nextPage = loadedPageLocked() + 1
        val parsed = fetchGuarded { fetchPage(nextPage, loadedReplyCountLocked()) }
        if (parsed == null) {
            // 后续页不再是主题页（主题被删除等）：停止分页，避免 UI 反复触发加载
            pageCount = loadedPageLocked()
            return
        }
        applyPageLocked(nextPage, parsed)
    }

    private suspend fun applyPageLocked(page: Int, parsed: V2exHtmlParser.ParsedTopicHtml) {
        pages[page] = parsed.replies
        pageCount = maxOf(parsed.pageCount, pages.lastKey())
        parsed.replyCount?.let { totalReplyCount = it }
        if (page == 1) {
            header = parsed
            cacheHeaderLocked(parsed)
        }
    }

    private suspend fun cacheHeaderLocked(parsed: V2exHtmlParser.ParsedTopicHtml) {
        localDataSource.cacheTopicDetail(
            TopicDetail(
                topic = parsed.toTopic(
                    replyCount = totalReplyCount ?: loadedReplyCountLocked(),
                ),
                content = "",
                contentRendered = parsed.contentRendered,
                replies = emptyList(),
            ),
        )
    }

    private fun snapshotLocked(): TopicDetailSnapshot {
        jsonDetail?.let { detail ->
            return TopicDetailSnapshot(
                detail = detail,
                loadedPageCount = 1,
                totalPageCount = 1,
                hasMore = false,
            )
        }
        val header = checkNotNull(header) { "Pager snapshot requested before first page load" }
        val rawReplies = pages.values.flatten()
        val totalCount = totalReplyCount?.coerceAtLeast(rawReplies.size) ?: rawReplies.size
        return TopicDetailSnapshot(
            detail = TopicDetail(
                topic = header.toTopic(replyCount = totalCount),
                content = "",
                contentRendered = header.contentRendered,
                replies = rawReplies.withReferencePreviews(),
                viewCount = header.viewCount,
                hotReplyCount = header.hotReplyCount,
                tags = header.tags,
            ),
            loadedPageCount = loadedPageLocked(),
            totalPageCount = pageCount,
            hasMore = hasMoreLocked(),
        )
    }

    /**
     * 带 access-denied 世代判定的单次请求：
     * 权限拒绝时推进世代、清空缓存与分页状态；成功时若世代已被更新的拒绝推进，
     * 较早的成功结果不允许生效。语义与 TopicRepositoryImpl 原有 topicDetail 保持一致。
     */
    private suspend fun <T> fetchGuarded(block: suspend () -> T): T {
        val requestGeneration = accessState.mutex.withLock { accessState.generation }
        val value = try {
            block()
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            if (error.isAccessDenied()) {
                accessState.mutex.withLock {
                    accessState.generation += 1
                    accessState.accessDeniedError = error
                    try {
                        localDataSource.clearTopicDetail(topicId)
                    } catch (clearError: CancellationException) {
                        throw clearError
                    } catch (clearError: Throwable) {
                        if (clearError !== error) error.addSuppressed(clearError)
                    }
                }
                resetLocked()
            }
            throw error
        }
        accessState.mutex.withLock {
            if (accessState.generation != requestGeneration) {
                accessState.accessDeniedError?.let { throw it }
                throw CancellationException("Topic detail page request was superseded")
            }
            accessState.accessDeniedError = null
        }
        return value
    }

    private suspend fun firstLoadFallback(error: Throwable): Result<TopicDetailSnapshot> {
        if (error.isAccessDenied()) return Result.failure(error)
        accessState.mutex.withLock {
            accessState.accessDeniedError?.let { return Result.failure(it) }
        }
        // 缓存不含回复，只对无回复主题是完整结果；不完整缓存不能当成功返回
        val cached = try {
            localDataSource.topicDetail(topicId)
                ?.takeIf { it.replies.isNotEmpty() || it.topic.replyCount == 0 }
        } catch (cacheError: CancellationException) {
            throw cacheError
        } catch (cacheError: Throwable) {
            error.addSuppressed(cacheError)
            null
        }
        return if (cached != null) {
            Result.success(
                TopicDetailSnapshot(
                    detail = cached,
                    loadedPageCount = 1,
                    totalPageCount = 1,
                    hasMore = false,
                ),
            )
        } else {
            Result.failure(error)
        }
    }
}

internal fun V2exHtmlParser.ParsedTopicHtml.toTopic(replyCount: Int): Topic = Topic(
    id = id,
    title = title,
    url = "https://www.v2ex.com/t/$id",
    node = Node(name = nodeName, title = nodeTitle),
    author = User(username = authorName, avatarUrl = authorAvatarUrl),
    avatarUrl = authorAvatarUrl,
    replyCount = replyCount,
    createdAtEpochSeconds = createdAtEpochSeconds,
)
