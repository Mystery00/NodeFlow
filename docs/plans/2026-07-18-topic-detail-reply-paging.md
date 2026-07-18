# 主题详情回复按需分页实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 主题详情改为按页加载回复：进入只抓第 1 页，滚动/楼层定位/刷新按需补页。

**Architecture:** 新增有状态 `TopicDetailPager`（domain 接口 + data 实现），封装页累积、引用预览增强、access-denied 世代与缓存交互；ViewModel 只消费快照。通知引用增强复用 `loadUntilFloor`。设计详见 [设计文档](2026-07-18-topic-detail-reply-paging-design.md)。

**Tech Stack:** Kotlin、Coroutines/Flow、Jsoup、Retrofit/OkHttp、Koin、Jetpack Compose、JUnit4 + Truth + MockWebServer。

## Global Constraints

- 沟通/注释/文档中文，日志英文；不新增 XML layout。
- 遵守 core/data/domain/feature 边界；UI 与 ViewModel 不直接访问网络、Room、DataStore。
- 网络测试不得访问真实 V2EX；页请求不自动重试；`CancellationException` 一律直接抛出。
- 不修改 User-Agent、Referer、页面分类规则；不把受限页解析为业务内容。
- 按 AGENTS.md 约定，本计划不执行 git 提交；每任务终点为"局部测试通过"检查点，最终统一全量验证。
- 回归底线：`.\gradlew.bat :app:testDebugUnitTest` 与 `.\gradlew.bat :app:assembleDebug` 全部通过。

---

### Task 1: Parser 支持总回复数与楼层偏移

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`

**Interfaces:**
- Produces: `parseTopicHtml(topicId: Long, html: String, floorOffset: Int = 0): ParsedTopicHtml?`；`ParsedTopicHtml` 新增 `replyCount: Int?` 字段。

- [ ] **Step 1: 写失败测试**

在 `V2exHtmlParserTest.kt` 追加：

```kotlin
@Test
fun parseTopicHtml_readsTotalReplyCountFromHeader() {
    val html = """
        <html>
          <body>
            <h1>分页主题</h1>
            <div class="topic_content">正文</div>
            <div class="cell"><span class="gray">342 条回复 &nbsp;•&nbsp; 到目前为止</span></div>
            <div id="r_1">
              <span class="no">1</span>
              <strong><a href="/member/alice">alice</a></strong>
              <div class="reply_content">第一条回复</div>
            </div>
          </body>
        </html>
    """.trimIndent()

    val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html))

    assertThat(topic.replyCount).isEqualTo(342)
}

@Test
fun parseTopicHtml_replyCountIsNullWhenHeaderMissing() {
    val html = """
        <html>
          <body>
            <h1>无回复主题</h1>
            <div class="topic_content">正文</div>
          </body>
        </html>
    """.trimIndent()

    val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html))

    assertThat(topic.replyCount).isNull()
}

@Test
fun parseTopicHtml_fallbackFloorUsesFloorOffset() {
    // span.no 缺失时，第 2 页的兜底楼层应从偏移量继续，而不是从 1 重新开始
    val html = """
        <html>
          <body>
            <h1>分页主题</h1>
            <div id="r_201">
              <strong><a href="/member/alice">alice</a></strong>
              <div class="reply_content">第二页第一条</div>
            </div>
            <div id="r_202">
              <strong><a href="/member/bob">bob</a></strong>
              <div class="reply_content">第二页第二条</div>
            </div>
          </body>
        </html>
    """.trimIndent()

    val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html, floorOffset = 100))

    assertThat(topic.replies.map { it.floor }).containsExactly(101, 102).inOrder()
}

@Test
fun parseTopicHtml_explicitFloorIgnoresFloorOffset() {
    val html = """
        <html>
          <body>
            <h1>分页主题</h1>
            <div id="r_201">
              <span class="no">150</span>
              <strong><a href="/member/alice">alice</a></strong>
              <div class="reply_content">显式楼层</div>
            </div>
          </body>
        </html>
    """.trimIndent()

    val topic = requireNotNull(parser.parseTopicHtml(topicId = 1L, html = html, floorOffset = 100))

    assertThat(topic.replies.single().floor).isEqualTo(150)
}
```

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest"`
Expected: FAIL（`replyCount`/`floorOffset` 不存在，编译错误）。

- [ ] **Step 3: 实现**

`V2exHtmlParser.kt` 修改点：

```kotlin
fun parseTopicHtml(topicId: Long, html: String, floorOffset: Int = 0): ParsedTopicHtml? {
    // ... 现有逻辑不变 ...
    val replies = replyElements.parseTopicReplies(topicId, floorOffset)
    // ...
    return ParsedTopicHtml(
        // ... 现有字段 ...
        replyCount = document.select("div.cell span.gray")
            .firstNotNullOfOrNull { REPLY_COUNT_REGEX.find(it.text())?.groupValues?.get(1)?.toIntOrNull() },
        pageCount = pageCount,
        replies = replies,
    )
}

private fun List<Element>.parseTopicReplies(topicId: Long, floorOffset: Int): List<Reply> =
    mapIndexedNotNull { index, element ->
        // ...
        val floor = element.selectFirst("span.no")?.text()?.firstInt()
            ?: (floorOffset + index + 1)
        // ...
    }
```

`ParsedTopicHtml` 增加 `val replyCount: Int? = null`（放在 `pageCount` 之前）。伴生常量：

```kotlin
private val REPLY_COUNT_REGEX = Regex("""(\d+)\s*条回复""")
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest"`
Expected: PASS（含全部既有用例）。

---

### Task 2: RemoteDataSource 单页接口

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSource.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSourceTest.kt`

**Interfaces:**
- Consumes: Task 1 的 `parseTopicHtml(topicId, html, floorOffset)`。
- Produces: `suspend fun topicDetailPage(topicId: Long, page: Int, floorOffset: Int): V2exHtmlParser.ParsedTopicHtml?`（null = 页面不是主题）；`suspend fun jsonTopicDetailFallback(topicId: Long): TopicDetail`。旧 `topicDetail` 本任务保留不动（Task 6 删除）。

- [ ] **Step 1: 写失败测试**（参照该测试类既有 MockWebServer 风格）

```kotlin
@Test
fun topicDetailPage_requestsGivenPageAndAppliesFloorOffset() = runTest {
    // 排入一个带 r_x 回复行、无 span.no 的主题页 HTML
    // 断言：请求路径为 /t/1221181?p=2；返回回复楼层从 floorOffset+1 开始
}

@Test
fun topicDetailPage_firstPageOmitsPageQuery() = runTest {
    // page = 1 时请求路径为 /t/1221181（不带 ?p=）
}

@Test
fun topicDetailPage_returnsNullForNonTopicHtml() = runTest {
    // 排入无主题结构的 HTML，断言返回 null 而不是抛异常
}
```

具体 HTML fixture 与断言写法复制该文件中现有 `topicDetail` 用例的模式（MockWebServer + `server.takeRequest().path`）。

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.TopicRemoteDataSourceTest"`
Expected: FAIL（方法不存在，编译错误）。

- [ ] **Step 3: 实现**

`TopicRemoteDataSource.kt` 追加（旧方法暂不删）：

```kotlin
suspend fun topicDetailPage(
    topicId: Long,
    page: Int,
    floorOffset: Int,
): V2exHtmlParser.ParsedTopicHtml? = safeNetworkCall {
    parser.parseTopicHtml(
        topicId = topicId,
        html = api.topicHtml(topicId, page = page.takeIf { it > 1 })
            .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic),
        floorOffset = floorOffset,
    )
}

suspend fun jsonTopicDetailFallback(topicId: Long): TopicDetail = jsonTopicDetail(topicId)
```

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.TopicRemoteDataSourceTest"`
Expected: PASS。

---

### Task 3: TopicDetailPager 领域接口与 data 实现

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/topic/TopicDetailPager.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/topic/TopicDetailPagerImpl.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/topic/TopicRepository.kt`（加 `fun topicDetailPager(topicId: Long): TopicDetailPager`）
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImpl.kt`（实现工厂；`TopicAccessState` 从 private 内部类提为同包 internal 类）
- Test: `app/src/test/java/app/mystery0/nodeflow/data/topic/TopicDetailPagerImplTest.kt`

**Interfaces:**
- Consumes: Task 2 的 `topicDetailPage` / `jsonTopicDetailFallback`；现有 `withReferencePreviews()`、`TopicLocalDataSource`、`isAccessDenied()`。
- Produces:

```kotlin
interface TopicDetailPager {
    suspend fun loadFirst(forceRefresh: Boolean): Result<TopicDetailSnapshot>
    suspend fun loadNext(): Result<TopicDetailSnapshot>
    suspend fun loadUntilFloor(floor: Int): Result<TopicDetailSnapshot>
    suspend fun loadUntilLastPage(): Result<TopicDetailSnapshot>
    suspend fun refreshLoaded(): Result<TopicDetailSnapshot>
}

data class TopicDetailSnapshot(
    val detail: TopicDetail,
    val loadedPageCount: Int,
    val totalPageCount: Int,
    val hasMore: Boolean,
)
```

**实现要点（`TopicDetailPagerImpl`，全部状态仅在内部 `Mutex` 持锁时读写）：**

```kotlin
class TopicDetailPagerImpl(
    private val topicId: Long,
    private val fetchPage: suspend (page: Int, floorOffset: Int) -> V2exHtmlParser.ParsedTopicHtml?,
    private val fetchJsonFallback: suspend () -> TopicDetail,
    private val localDataSource: TopicLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
    private val accessState: TopicAccessState,
) : TopicDetailPager
```

（`TopicRepositoryImpl` 工厂里把两个 lambda 绑定到真实 `TopicRemoteDataSource`，便于测试注入，风格参照 `MemberTagRepositoryImpl`。）

- 状态：`header: ParsedTopicHtml?`（第 1 页元数据）、`jsonDetail: TopicDetail?`（JSON 兜底全量）、`pages: SortedMap<Int, List<Reply>>`（原始回复）、`pageCount: Int`、`totalReplyCount: Int?`。
- `fetchPage(page, floorOffset)`：持 `accessState.mutex` 快照 generation → 调 `topicDetailPage` → 失败时：denied 则 generation+1、记录错误、`clearTopicDetail`、清空 pager 状态后抛出；成功后回锁校验 generation 未变（变了抛被 supersede 的 `CancellationException` 或记录的 denied 错误），清空 `accessDeniedError`。语义完整复刻现 `TopicRepositoryImpl.topicDetail` 的世代逻辑。
- `applyPageLocked(page, parsed)`：写入 `pages[page]`；`pageCount = parsed.pageCount`；`parsed.replyCount` 非空时更新 `totalReplyCount`；`page == 1` 时更新 `header` 并 `localDataSource.cacheTopicDetail(...)`。
- `loadFirst(forceRefresh)`：已有数据且非强刷直接返回快照；否则抓第 1 页——HTML 解析为 null 或非 denied 失败时走 `jsonTopicDetailFallback`（成功也写缓存）；两路都失败时按现有"缓存回退仅当 `replies.isNotEmpty() || replyCount == 0`"规则尝试缓存正文（命中则返回 `hasMore = false` 的快照且不写入 pager 状态），否则返回 failure。
- `loadNext()`：无更多页时原样返回快照；否则抓 `maxLoadedPage + 1`（`floorOffset = 已加载条数`）。
- `loadUntilFloor(floor)`：未加载先走 `loadFirst`；循环 `loadNext` 直到 `已加载条数 >= floor` 或无更多页；中途失败返回 failure 但保留已加载前缀。
- `loadUntilLastPage()`：同上循环到 `!hasMore`。
- `refreshLoaded()`：未加载或 JSON 兜底态 → 等价 `loadFirst(forceRefresh = true)`；否则把第 1..maxLoadedPage 页抓到临时 map（每页 `floorOffset` 累加，`pageCount` 缩小时提前止步），全部成功才原子替换状态并写缓存，失败则原状态返回 failure。
- `snapshotLocked()`：`jsonDetail` 存在 → `TopicDetailSnapshot(jsonDetail, 1, 1, hasMore = false)`；否则拼接 `pages` → `withReferencePreviews()`，`Topic.replyCount = totalReplyCount?.coerceAtLeast(loaded) ?: loaded`，`hasMore = maxLoadedPage < pageCount`。`Topic` 组装复用现 `ParsedTopicHtml.toTopic`（从 RemoteDataSource 移到本文件或共享 internal 函数）。
- 所有对外方法 `withContext(ioDispatcher)` + 内部 mutex；`CancellationException` 直接抛。

`TopicRepositoryImpl` 增加：

```kotlin
override fun topicDetailPager(topicId: Long): TopicDetailPager = TopicDetailPagerImpl(
    topicId = topicId,
    fetchPage = { page, floorOffset ->
        remoteDataSource.topicDetailPage(topicId, page, floorOffset)
    },
    fetchJsonFallback = { remoteDataSource.jsonTopicDetailFallback(topicId) },
    localDataSource = localDataSource,
    ioDispatcher = ioDispatcher,
    accessState = topicAccessStates.computeIfAbsent(topicId) { TopicAccessState() },
)
```

- [ ] **Step 1: 写失败测试**（fake `TopicRemoteDataSource` 不可行——类非 open，测试通过 MockWebServer 或将页抓取抽为构造器注入的 suspend lambda；选后者：`TopicDetailPagerImpl` 构造参数改为 `fetchPage: suspend (page: Int, floorOffset: Int) -> ParsedTopicHtml?` 与 `fetchJsonFallback: suspend () -> TopicDetail`，`TopicRepositoryImpl` 工厂里绑定真实 RemoteDataSource，参照 `MemberTagRepositoryImpl` 的既有注入风格）

测试用例清单（每条一个 `@Test`，fake lambda 按页返回构造的 `ParsedTopicHtml`）：

1. `loadFirst_returnsFirstPageOnly`：pageCount=3 时只请求第 1 页，`hasMore` 为 true，`replyCount` 取解析总数。
2. `loadFirst_secondCallReturnsCachedSnapshotWithoutFetch`：请求计数不增加。
3. `loadNext_appendsPagesInOrder`：楼层连续、`loadedPageCount` 递增。
4. `loadNext_afterLastPageIsNoOp`。
5. `loadUntilFloor_loadsAcrossPages`：目标 250 楼 → 请求到第 3 页。
6. `loadUntilFloor_stopsAtLastPageWhenFloorExceedsTotal`。
7. `loadUntilFloor_midFailureKeepsLoadedPrefix`：第 3 页抛 IOException → Result.failure，随后 `loadFirst(false)` 返回含前 2 页的快照。
8. `refreshLoaded_refetchesAllLoadedPagesAtomically`：改变 fake 数据后刷新，全量替换。
9. `refreshLoaded_midFailureKeepsOldState`。
10. `loadFirst_fallsBackToJsonWhenHtmlIsNotTopic`：fetchPage 返回 null → jsonDetail、`hasMore = false`。
11. `accessDenied_clearsCacheAndPropagates`：denied 后 `clearTopicDetail` 被调、后续快照为空态。
12. `crossPageReferencePreview`：第 2 页回复 `@alice #50` 能引用第 1 页 50 楼生成 excerpt。

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.TopicDetailPagerImplTest"`
Expected: FAIL（类不存在）。

- [ ] **Step 3: 按上述要点实现**（接口、实现、Repository 工厂、`TopicAccessState` 提为 internal）

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.*"`
Expected: PASS（含既有 `TopicRepositoryImplTest`）。

---

### Task 4: ViewModel 与详情页 UI 接入

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailUiState.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailUiEvent.kt`（新增 `LoadMoreReplies`）
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/topic/GetTopicDetailUseCase.kt`（`operator fun invoke(topicId: Long): TopicDetailPager = repository.topicDetailPager(topicId)`；旧 suspend 签名删除，Task 6 前编译依赖已迁移）
- Test: `app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModelTest.kt`（重写 fake 为 `FakeTopicDetailPager`）

**Interfaces:**
- Consumes: Task 3 的 `TopicDetailPager` / `TopicDetailSnapshot`。
- Produces: `TopicDetailUiState` 新增 `hasMoreReplies: Boolean = false`、`isLoadingMore: Boolean = false`、`loadMoreError: String? = null`。

**ViewModel 行为规格：**

- 构造注入 `GetTopicDetailUseCase`，`init` 中 `pager = getTopicDetailPager(topicId)`；`replyFloor` 从 `SavedStateHandle` 读取：`savedStateHandle.get<Int>("replyFloor")?.takeIf { it > 0 }`。
- 初始加载：`loadFirst(false)` → 成功映射快照；若深链楼层存在且 `hasMore && replies.size < floor`，继续 `loadUntilFloor(floor)`（期间 `isLoadingMore = true`），完成后设置 `replyFloorTarget = floor`（成功或失败都设置，失败额外置顶部 `errorMessage` 并用 `loadFirst(false)` 同步已加载前缀）。
- `LoadMoreReplies`：`isLoadingMore || isRefreshing || !hasMoreReplies` 时忽略；`loadNext()` 失败置 `loadMoreError`，成功清空。
- `Refresh`/`Retry`：`detail == null` → `loadFirst(forceRefresh = true)`（isLoading 语义与现状一致）；否则 `refreshLoaded()`（`isRefreshing = true`，失败保留数据置 `errorMessage`）。
- `ReplyCreated(floor)`：`loadUntilFloor(floor)` 后设置 `replyFloorTarget`（替代现全量强刷），失败同深链处理。
- access denied（任何操作）：`detail = null` + `errorMessage`，与现状一致。
- 快照映射：`hasMoreReplies = snapshot.hasMore`；`detail = snapshot.detail`。
- 保留现有 `loadGeneration` 防过期覆盖。

**Screen 改动规格：**

- `TopicDetailContent` 增参 `hasMoreReplies`、`isLoadingMore`、`loadMoreError`、`onLoadMore: () -> Unit`。
- 回复 `items` 之后追加 footer：

```kotlin
if (hasMoreReplies || loadMoreError != null) {
    item(key = "reply-load-more") {
        ReplyLoadMoreFooter(
            isLoading = isLoadingMore,
            errorMessage = loadMoreError,
            onRetry = onLoadMore,
        )
    }
}
```

`ReplyLoadMoreFooter`：加载中为居中 `CircularProgressIndicator`（24.dp）加"正在加载更多回复"；错误态为错误文案 + `TextButton("重试")`。

- 自动触发（提前 10 项）：

```kotlin
LaunchedEffect(listState, hasMoreReplies, isLoadingMore, loadMoreError) {
    if (!hasMoreReplies || isLoadingMore || loadMoreError != null) return@LaunchedEffect
    snapshotFlow {
        val info = listState.layoutInfo
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        lastVisible >= info.totalItemsCount - LOAD_MORE_PREFETCH_ITEMS
    }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) onLoadMore() }
}
```

`private const val LOAD_MORE_PREFETCH_ITEMS = 10`。

- 楼层滚动 `LaunchedEffect` 的 key 增加 `targetFloorLoaded`（`detail.replies.size >= (replyFloorTarget ?: initialReplyFloor ?: 0)`），保证补齐完成后能重新触发定位；`replyFloorTarget` 未命中且 `hasMoreReplies` 时不执行"滚到底"兜底（等补齐结束）。
- `ReplySummaryRow` 增参 `hasMore: Boolean`：`hasMore` 时文案为 `"共 ${topic.replyCount} 条回复 · 已加载 ${replies.size} 条"`，否则维持现文案。
- `NodeFlowNavHost` 无需改动（`initialReplyFloor` 继续传给 Screen 做滚动）。

- [ ] **Step 1: 重写 ViewModel 测试**（`FakeTopicDetailPager` 实现 `TopicDetailPager`，各方法从可编程队列出队）用例：

1. 初始加载只调 `loadFirst`，快照映射 `hasMoreReplies`。
2. 深链楼层未加载时自动 `loadUntilFloor` 并最终设置 `replyFloorTarget`。
3. `LoadMoreReplies` 调 `loadNext`；`isLoadingMore` 置位/复位；失败写 `loadMoreError`。
4. `hasMoreReplies = false` 时 `LoadMoreReplies` 不调 pager。
5. 有数据时 `Refresh` 调 `refreshLoaded`，失败保留 detail 且写 `errorMessage`。
6. 无数据时 `Retry` 调 `loadFirst(true)`。
7. `ReplyCreated(5)` 调 `loadUntilFloor(5)` 后设置 `replyFloorTarget = 5`。
8. access denied 清空 detail（迁移现有两条用例语义）。

- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.topicdetail.*"`
Expected: FAIL。

- [ ] **Step 3: 按规格实现 ViewModel、UiState、UiEvent、UseCase、Screen**

- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.topicdetail.*"`
Expected: PASS（含 `ReplyFabVisibilityTest`、`TopicDetailMetadataTest`）。

---

### Task 5: 通知引用增强迁移到 loadUntilFloor

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/notification/NotificationReferenceEnricher.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/notification/NotificationRepositoryImpl.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/notification/NotificationReferenceEnricherTest.kt`

**Interfaces:**
- Consumes: `TopicRepository.topicDetailPager(topicId)`（Task 3）。
- Produces: `enrichNotificationReferences(notifications, loadRepliesUntilFloor: suspend (topicId: Long, floor: Int) -> Result<TopicDetail>)`。

**实现规格：**

- Enricher 签名改为按 `(topicId, floor)` 加载；同批次内维持 `failedTopics: MutableSet<Long>`——某主题一次失败后，同批其余通知直接跳过（保持现"每主题一次尝试"语义）。
- `NotificationRepositoryImpl.enrichReferences` 每批次创建 `mutableMapOf<Long, TopicDetailPager>()`，`getOrPut` 后 `loadUntilFloor(floor).map { it.detail }`；同主题不同楼层靠 pager 增量补齐。

- [ ] **Step 1: 更新测试**（用例：同主题两条通知只建一个 pager 且第二次楼层更深时增量加载；加载失败后同主题后续通知不再尝试；目标楼层缺失时通知保持原样——迁移现有用例到新签名）
- [ ] **Step 2: 运行确认失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.notification.*"`
Expected: FAIL。

- [ ] **Step 3: 实现**
- [ ] **Step 4: 运行确认通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.notification.*"`
Expected: PASS。

---

### Task 6: 删除旧全量路径并迁移遗留测试

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/topic/TopicRepository.kt`（删 `topicDetail`）
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImpl.kt`（删 `topicDetail` 与其中世代逻辑——已由 pager 承载；保留 `topicAccessStates` 与工厂）
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSource.kt`（删 `topicDetail`/`htmlTopicDetail` 全量循环；`jsonTopicDetail` 仅保留给 `jsonTopicDetailFallback`）
- Test: `app/src/test/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImplTest.kt`、`TopicRemoteDataSourceTest.kt`（删除/迁移旧 `topicDetail` 用例——凡语义已被 `TopicDetailPagerImplTest` 覆盖的直接删除，未覆盖的补进 pager 测试）
- Test: `app/src/test/java/app/mystery0/nodeflow/di/NodeFlowKoinModuleTest.kt`（如有 `GetTopicDetailUseCase` 相关校验则确认仍通过）

- [ ] **Step 1: 全局搜索确认无残留调用**

Run: `grep -rn "topicDetail(" app/src/main/java`（应只剩 `topicDetailPage`/`topicDetailPager`/`LocalDataSource.topicDetail`）
- [ ] **Step 2: 删除并迁移测试**
- [ ] **Step 3: 运行全部主题相关测试**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.*" --tests "app.mystery0.nodeflow.di.*"`
Expected: PASS。

---

### Task 7: 文档同步与全量验证

**Files:**
- Modify: `docs/subsystems/` 中描述主题详情"全量抓取所有回复页"的段落（先 `grep -rn "回复" docs/subsystems/` 定位，逐段改为按需分页语义）
- Verify: `docs/index.md` 已含设计与本计划链接（前置任务已加）

- [ ] **Step 1: 更新子系统文档**
- [ ] **Step 2: 全量单元测试**

Run: `.\gradlew.bat :app:testDebugUnitTest`
Expected: PASS。

- [ ] **Step 3: Debug 构建**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 4: Lint**

Run: `.\gradlew.bat :app:lintDebug`
Expected: BUILD SUCCESSFUL（无新增 error）。

- [ ] **Step 5: 汇报**

最终回复中说明：模拟器/真机的滚动加载、深链定位、刷新与发帖定位验证未在本环境执行，需要用户真机确认。
