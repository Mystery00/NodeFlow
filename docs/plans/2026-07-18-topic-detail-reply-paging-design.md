# 主题详情回复按需分页设计

## 背景

主题详情当前由 `TopicRemoteDataSource.htmlTopicDetail` 一次性抓取：解析第 1 页后，若 `pageCount > 1` 则串行抓取剩余所有回复页，全部拼接完成才返回渲染。V2EX 网页每页 100 条回复，20 页的热帖需要连发 20 个请求才能看到首屏，首屏延迟和流量在大帖场景明显劣化。

本设计将主题详情改为按需分页：先渲染第 1 页，滚动接近尾部时自动加载下一页；楼层定位场景顺序补齐到目标页。

现有实现中与分页直接相关的事实：

- 引用预览（`ReplyReferenceBuilder.withReferencePreviews`）只向更早楼层查找，因此只要按页顺序累积，已加载前缀内的引用预览始终完整。
- 回复楼层号取自 HTML 的 `span.no`（全局楼层）；仅在缺失时退回页内序号 `index + 1`，该兜底在第 2 页起会产生错误楼层，需要按页偏移修正。
- 总回复数当前用 `replies.size` 计算，分页后不再成立，需要从页面头部解析真实总数。
- Room 不缓存回复，只缓存正文；缓存回退仅对无回复主题可用。
- `TopicRepositoryImpl` 维护按主题的 access-denied 世代逻辑，防止过期成功响应覆盖较新的权限拒绝。

## 目标

- 进入主题详情只抓取第 1 页即渲染，正文与前 100 条回复立即可读。
- 滚动接近已加载内容尾部时自动加载下一页，底部显示加载指示器，失败时显示重试。
- 通知深链带 `replyFloor` 进入时，顺序补齐第 1 页到目标楼层所在页后滚动定位。
- 发表回复成功后，顺序补齐到最后一页并定位、高亮新楼层。
- 顶栏刷新与重试重拉已加载的所有页，保持滚动位置与阅读进度。
- 回复区显示真实总回复数与已加载进度。
- 引用预览、引用回跳、楼主标识、楼层高亮等现有能力在分页下行为不变。

## 非目标

- 不改变首页与节点主题列表的分页方式。
- 不引入 Paging 3 承载回复列表。
- 不把回复写入 Room 缓存，不改变正文缓存与离线回退语义。
- 不支持“直接跳转目标页、中间留空洞”的非连续加载。
- 不修改回复编辑器、图片上传与草稿逻辑。

## 架构与组件边界

新增有状态的 `TopicDetailPager`，分页状态全部封装在 data 层，ViewModel 只消费快照：

```text
TopicDetailScreen
  → TopicDetailUiEvent（LoadMoreReplies / Refresh / ReplyCreated …）
TopicDetailViewModel
  → TopicDetailPager（每个 ViewModel 一个实例）
TopicDetailPagerImpl（data.topic）
  → TopicRemoteDataSource.topicDetailPage(topicId, page)
  → withReferencePreviews()（累积前缀整体增强）
  → TopicLocalDataSource（正文缓存，行为不变）
```

### Domain 接口

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

- `TopicDetailSnapshot.detail.replies` 为已加载前缀，且已完成引用预览增强。
- 真实总回复数写入 `detail.topic.replyCount`；解析不到总数时以已加载条数兜底。
- pager 由 `TopicRepository.topicDetailPager(topicId)` 创建；`GetTopicDetailUseCase` 改为返回 pager 的简单转发，不再逐方法包装。
- `TopicRepository.topicDetail` 全量接口删除。它的另一调用方——通知引用增强（`NotificationReferenceEnricher`）改为按批次持有每主题一个 pager，对每条通知执行 `loadUntilFloor(locator.floor)` 后在快照中查找目标楼层；通知场景由此从“全量抓取”降为“补齐到目标楼层”，流量同步收益。

### Data 实现

`TopicRemoteDataSource` 拆出单页接口，删除现有全量循环：

- `topicDetailPage(topicId, page)` 返回该页原始回复、`pageCount`、总回复数；第 1 页额外携带正文与主题元数据。
- 第 1 页 HTML 无法解析为主题时，走现有 JSON 兜底一次取得全量回复，pager 将其视为单页、`hasMore = false`。

`TopicDetailPagerImpl` 内部状态与规则：

- 用 `Mutex` 串行化全部操作；状态为 `pages: MutableMap<Int, List<Reply>>`（原始回复）加主题元数据、`pageCount`、总回复数。
- 每次状态变更后按页序拼接原始回复，整体执行一次 `withReferencePreviews()` 生成快照。
- `loadFirst(forceRefresh = false)` 在已加载时直接返回当前快照；`forceRefresh = true` 清空状态重新抓第 1 页。
- `loadNext` 抓取 `loadedPageCount + 1` 页并追加；没有更多页时直接返回当前快照。
- `loadUntilFloor(floor)` 循环 `loadNext` 直至已加载条数覆盖目标楼层或没有更多页，不对每页条数做数学假设。
- `loadUntilLastPage` 循环 `loadNext` 到最后一页。
- `refreshLoaded` 串行重拉第 1..N 页，全部成功后原子替换状态；中途失败保留原状态并返回失败。
- 首次加载失败时沿用现有缓存回退语义（仅无回复主题可用缓存正文）。
- access-denied 判定与正文缓存写入沿用 `TopicRepositoryImpl` 现有世代逻辑：页请求成功写缓存、失败按世代判断回退，权限拒绝时清空该主题缓存并向上传播。

### Parser 改动

- `ParsedTopicHtml` 增加 `replyCount: Int?`，从回复区头部“N 条回复”解析；无回复主题为 null。
- `parseTopicReplies` 增加 `floorOffset` 参数，楼层兜底改为 `floorOffset + index + 1`；`span.no` 存在时行为不变。

## 交互与 UI

- `TopicDetailUiState` 增加 `hasMoreReplies`、`isLoadingMore`、`loadMoreError`；新增事件 `LoadMoreReplies`。
- ViewModel 从 `SavedStateHandle` 读取 `replyFloor`：首屏加载完成后目标楼层未加载时自动 `loadUntilFloor` 补齐，Screen 滚动高亮逻辑不变。
- `Refresh` / `Retry`：已有成功数据时执行 `refreshLoaded`，否则 `loadFirst(forceRefresh = true)`。
- `ReplyCreated(floor)`：执行 `loadUntilFloor(floor)` 后设置 `replyFloorTarget`，替代现有全量强刷。
- 回复列表末尾新增 footer item：`hasMore` 时显示加载指示器；滚动接近尾部（提前约 10 项）自动派发 `LoadMoreReplies`；`loadMoreError` 非空时 footer 显示错误与重试按钮。
- `ReplySummaryRow` 在部分加载时显示“共 N 条回复 · 已加载 M 条”，全量加载后显示“共 N 条回复”。

## 错误处理

- 首次加载失败：与现状一致，全屏错误或缓存回退。
- `loadNext` 失败：只影响 footer 重试，不打断已读内容。
- 补齐（深链 / 发帖定位）中途失败：停在已加载前缀，顶部 error banner 提示，定位退回已加载末尾（沿用现有 fallback 行为）。
- `refreshLoaded` 失败：数据不动，顶部 error banner。
- access denied：任何操作中出现都清空 detail 并显示错误，与现状一致。
- 所有页请求不自动重试；`CancellationException` 一律直接抛出。

## 测试设计

### Parser

- 总回复数解析：有回复、无回复、多页 fixture。
- `floorOffset` 兜底：`span.no` 缺失时第 2 页楼层正确偏移；`span.no` 存在时不受影响。

### TopicDetailPagerImpl（新增测试类）

- 首页加载即返回、`hasMore` 与总数正确。
- `loadNext` 顺序追加、末页后为 no-op。
- `loadUntilFloor` 跨页补齐、目标超出总楼层时停在最后一页。
- `loadUntilLastPage` 循环到底。
- `refreshLoaded` 全部成功原子替换、中途失败保留原状态。
- JSON 兜底退化为单页、`hasMore = false`。
- access denied 清缓存并传播、过期成功不覆盖较新拒绝。
- 跨页引用预览：第 2 页回复引用第 1 页楼层可正确生成预览。

### RemoteDataSource 与 ViewModel

- `topicDetailPage` 单页抓取、第 1 页与后续页的字段差异。
- ViewModel：滚动加载事件、刷新语义分支、`ReplyCreated` 补齐定位、深链 `replyFloor` 自动补齐、加载失败状态映射。
- 通知引用增强：同批次同主题复用同一 pager、不同楼层增量补齐、目标楼层缺失时保持通知原样。
- Koin module 校验新增注册。
- 全部网络测试使用假数据或 MockWebServer，不访问真实 V2EX。

## 验收标准

- 多页主题进入详情只发第 1 页请求即完成首屏渲染。
- 滚动到已加载尾部附近自动追加下一页，失败可从 footer 重试。
- 通知深链跳转高楼层能自动补齐并定位高亮。
- 大帖中发表回复成功后补齐到最后一页并定位新楼层。
- 刷新重拉已加载页且滚动位置不丢失。
- 回复区正确显示总回复数与加载进度。
- 相关局部测试、全量 `testDebugUnitTest` 和 `assembleDebug` 通过。
- 模拟器验证滚动加载、深链定位、刷新与发帖定位的真实交互。
