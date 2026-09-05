# 主题正文原生富文本渲染实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将主题正文和附言迁移为无 WebView 的原生富文本，同时保留表格、图片、视频和链接交互。

**Architecture:** 使用 Jsoup 将 HTML 解析为与 Compose 无关的 `RichContentDocument`，再由多个小型 Compose 组件按块渲染。主题正文块直接进入现有 `LazyColumn`，文字断行交给 Compose，图片交给 Coil，视频交给 Media3，表格使用可横向滚动的原生网格布局。

**Tech Stack:** Kotlin 2.4.10、Jetpack Compose BOM 2026.08.00、Material 3、Jsoup 1.23.2、Coil 2.7.0、Media3 1.11.0、JUnit 4、Google Truth

**Spec:** `docs/plans/2026-09-05-native-topic-rich-content-design.md`

**状态：** 已完成（2026-09-05）。实现采用单份单元格内容测量加独立背景网格，避免两阶段重复组合导致重复无障碍语义或重复媒体副作用。

**验证：** 全量 JVM 单元测试、Debug 构建、Lint、富文本 Compose instrumentation 测试均通过；Pixel 10 Pro（Android 17）模拟器已对目标长帖完成正文与回复列表多轮往返滚动录屏，正文只有一个纵向滚动容器且未再闪烁。

## Global Constraints

- 只迁移主题正文和附言；回复与通知继续使用现有 `HtmlText`。
- 主题正文和附言完全脱离 WebView，不保留内部纵向滚动兜底。
- 表格超宽时只允许局部横向滚动；代码块同样只允许局部横向滚动。
- iframe 不执行，只渲染可点击网页占位。
- 代码注释、KDoc 和文档使用中文；日志使用英文。
- 业务逻辑先写失败测试；网络测试不访问真实 V2EX。
- 未经用户明确要求，不提交、不推送 Git。

---

### Task 1: 定义富文本模型并实现 HTML 解析器

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/model/RichContent.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/parser/RichContentParser.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/core/parser/RichContentParserTest.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/V2exContentLinkify.kt`

**Interfaces:**
- Consumes: `ImageHostMatcher.shouldLoadAsImage(url: String, hosts: Collection<String>): Boolean`、`Element.linkifyPlainV2exTopicLinks()`
- Produces: `RichContentParser.parse(html: String, customImageHosts: Collection<String> = emptySet()): RichContentDocument`、`RichContentDocument.plainText(): String`

- [ ] **Step 1: 写入行内连续排版、DOM 顺序和安全过滤失败测试**

```kotlin
@Test
fun parse_keepsNestedInlineFormattingInOneParagraph() {
    val document = parser.parse("<p>前缀<strong>粗体 <a href='/t/12'>链接</a></strong>后缀</p>")

    assertThat(document.blocks).hasSize(1)
    val paragraph = document.blocks.single() as RichContentBlock.Paragraph
    assertThat(paragraph.content.filterIsInstance<RichInline.Text>().joinToString("") { it.value })
        .isEqualTo("前缀粗体 链接后缀")
    assertThat(paragraph.content.filterIsInstance<RichInline.Text>().single { it.value == "链接" }.linkUrl)
        .isEqualTo("https://www.v2ex.com/t/12")
}

@Test
fun parse_preservesTextImageAndFollowingTextOrder() {
    val document = parser.parse("<p>之前<img class='embedded_image' src='/a.png'>之后</p>")

    assertThat(document.blocks.map { it::class.simpleName })
        .containsExactly("Paragraph", "Image", "Paragraph").inOrder()
}

@Test
fun parse_dropsExecutableContentButKeepsUnknownWrapperChildren() {
    val document = parser.parse("<custom>保留<script>bad()</script><b>文字</b></custom>")

    assertThat(document.plainText()).isEqualTo("保留文字")
}
```

- [ ] **Step 2: 运行解析器测试并确认因类型尚未定义而失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.RichContentParserTest"
```

Expected: FAIL，提示 `RichContentParser`、`RichContentBlock` 或 `RichInline` 未定义。

- [ ] **Step 3: 建立不可变富文本模型**

在 `RichContent.kt` 定义：

```kotlin
data class RichContentDocument(val blocks: List<RichContentBlock>)

sealed interface RichContentBlock {
    data class Paragraph(val content: List<RichInline>, val alignment: RichTextAlignment? = null) : RichContentBlock
    data class Heading(val level: Int, val content: List<RichInline>) : RichContentBlock
    data class Quote(val blocks: List<RichContentBlock>) : RichContentBlock
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<RichListItem>) : RichContentBlock
    data class CodeBlock(val code: String) : RichContentBlock
    data class Table(val caption: List<RichInline>, val rows: List<RichTableRow>) : RichContentBlock
    data class Image(val image: RichImage) : RichContentBlock
    data class Video(val video: RichVideo) : RichContentBlock
    data class IframePlaceholder(val embed: RichEmbed) : RichContentBlock
    data object Divider : RichContentBlock
}

sealed interface RichInline {
    data class Text(val value: String, val style: RichInlineStyle = RichInlineStyle(), val linkUrl: String? = null) : RichInline
    data object LineBreak : RichInline
    data class InlineImage(val image: RichImage) : RichInline
}
```

同时定义 `RichInlineStyle`、`RichBaseline`、`RichTextAlignment`、`RichImage`、`RichVideoSource`、`RichVideo`、`RichEmbed`、`RichListItem`、`RichTableRow` 和 `RichTableCell`，并提供递归的 `RichContentDocument.plainText()` 供整体解析失败时降级与测试使用。所有模型只使用 Kotlin/JDK 类型，不引用 Compose、Android 或 Coil。

- [ ] **Step 4: 实现 Jsoup 递归解析和段落刷新器**

`RichContentParser` 使用解析上下文累积行内节点；只有遇到真正的块级标签或独立媒体时才调用 `flushParagraph()`。实现以下标签映射：

```kotlin
when (element.normalName()) {
    "p", "div", "section", "article" -> parseContainer(element, context)
    in HEADING_TAGS -> emitHeading(element)
    "blockquote" -> emitQuote(element)
    "ul", "ol" -> emitList(element)
    "pre" -> emitCodeBlock(element.wholeText())
    "table" -> emitTable(element)
    "img" -> emitImageOrInline(element)
    "video" -> emitVideo(element)
    "iframe" -> emitIframePlaceholder(element)
    "hr" -> emitDivider()
    in DROPPED_TAGS -> Unit
    else -> parseChildren(element)
}
```

`a`、`strong/b`、`em/i`、`u`、`del/s/strike`、`code`、`sup/sub`、`small/big`、`span/font` 通过不可变行内上下文叠加样式。解析前在 DOM 上调用 `linkifyPlainV2exTopicLinks()`，并把命中自定义图床的纯链接后插入带内部标记的图片节点。

- [ ] **Step 5: 补充表格、媒体、列表与异常 HTML 测试**

```kotlin
@Test
fun parse_extractsTableSpansVideoAndIframe() {
    val html = """
        <table><caption>参数</caption><tr><th colspan='2'>标题</th></tr><tr><td rowspan='2'>A</td><td>B</td></tr></table>
        <video poster='/poster.jpg'><source src='/movie.mp4' type='video/mp4'></video>
        <iframe title='演示' src='https://example.com/embed/1'></iframe>
    """.trimIndent()

    val document = parser.parse(html)

    val table = document.blocks[0] as RichContentBlock.Table
    assertThat(table.rows[0].cells.single().colSpan).isEqualTo(2)
    assertThat(table.rows[1].cells[0].rowSpan).isEqualTo(2)
    assertThat((document.blocks[1] as RichContentBlock.Video).video.sources.single().url)
        .isEqualTo("https://www.v2ex.com/movie.mp4")
    assertThat((document.blocks[2] as RichContentBlock.IframePlaceholder).embed.url)
        .isEqualTo("https://example.com/embed/1")
}
```

- [ ] **Step 6: 运行解析器测试并确认通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.RichContentParserTest"
```

Expected: BUILD SUCCESSFUL。

---

### Task 2: 实现行内文本、列表、引用和代码渲染

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/RichContentText.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/RichContentRenderer.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/RichContentRendererTest.kt`

**Interfaces:**
- Consumes: `RichContentBlock`、`RichInline`、`onUrlClick: (String) -> Boolean`
- Produces: `RichContentBlockView(block, onUrlClick, onImageClick, imageSizeCache)`、`buildRichAnnotatedText(content, baseStyle, colors, onLinkClick)`

- [ ] **Step 1: 写入行内内容合并和 CSS 颜色降级失败测试**

```kotlin
@Test
fun buildRichTextPlan_keepsAllInlineNodesInOneTextLayout() {
    val plan = buildRichTextPlan(
        listOf(
            RichInline.Text("普通"),
            RichInline.Text("粗体", RichInlineStyle(bold = true)),
            RichInline.LineBreak,
            RichInline.Text("链接", linkUrl = "https://www.v2ex.com/t/1"),
        ),
    )

    assertThat(plan.text).isEqualTo("普通粗体\n链接")
    assertThat(plan.ranges.map { it.value }).containsExactly("普通", "粗体", "链接").inOrder()
}

@Test
fun parseRichCssColor_rejectsInvalidOrTransparentInput() {
    assertThat(parseRichCssColor("#336699")).isNotNull()
    assertThat(parseRichCssColor("javascript:red")).isNull()
    assertThat(parseRichCssColor("transparent")).isNull()
}
```

- [ ] **Step 2: 运行局部测试并确认失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContentRendererTest"
```

Expected: FAIL，提示渲染计划函数尚未定义。

- [ ] **Step 3: 实现单个 `Text` 的行内渲染**

`RichContentText` 一次构建完整 `AnnotatedString`。每个 `RichInline.Text` 添加对应 `SpanStyle`；链接使用 `LinkAnnotation.Clickable`；`RichInline.InlineImage` 使用唯一 ID 和 `InlineTextContent`，占位固定为约 `1.2em × 1.2em`，不因异步加载改变行高。

外部链接处理固定为：

```kotlin
val openUrl: (String) -> Unit = { url ->
    if (!onUrlClick(url)) runCatching { uriHandler.openUri(url) }
}
```

- [ ] **Step 4: 实现递归块渲染入口**

`RichContentBlockView` 分派段落、标题、引用、列表、代码块、图片、表格、视频、iframe 与分隔线。引用和列表递归调用该入口；代码块使用 `horizontalScroll`，不添加垂直滚动修饰符。

- [ ] **Step 5: 运行局部测试并确认通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContentRendererTest"
```

Expected: BUILD SUCCESSFUL。

---

### Task 3: 实现稳定图片和官方 Compose 视频播放

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/RichContentMedia.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/RichContentMediaTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `RichImage`、`RichVideo`、现有 `ContentImageLoadingPlaceholder`、`ContentImageErrorPlaceholder`
- Produces: `RichContentImageSizeCache`、`rememberRichContentImageSizeCache(key)`、`selectVideoSource(sources: List<RichVideoSource>): RichVideoSource?`、`RichBlockImage`、`RichVideoPlayer`、`RichEmbedPlaceholder`

- [ ] **Step 1: 写入图片比例缓存和视频候选选择失败测试**

```kotlin
@Test
fun imageLayout_reusesResolvedAspectRatioAfterReattach() {
    val cache = RichContentImageSizeCache()
    cache.put("https://example.com/a.jpg", width = 1200, height = 800)

    assertThat(cache.aspectRatio("https://example.com/a.jpg", 0, 0)).isEqualTo(1.5f)
}

@Test
fun selectVideoSource_prefersDeclaredPlayableMp4ThenFallsBackInOrder() {
    val sources = listOf(
        RichVideoSource("https://example.com/a.bin", null),
        RichVideoSource("https://example.com/a.mp4", "video/mp4"),
    )

    assertThat(selectVideoSource(sources)?.url).endsWith("a.mp4")
}
```

- [ ] **Step 2: 运行媒体测试并确认失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContentMediaTest"
```

Expected: FAIL，提示缓存和媒体选择函数尚未定义。

- [ ] **Step 3: 添加 Media3 1.11.0 依赖**

在版本目录添加：

```toml
media3 = "1.11.0"
androidx-media3-exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
androidx-media3-exoplayer-hls = { group = "androidx.media3", name = "media3-exoplayer-hls", version.ref = "media3" }
androidx-media3-ui-compose-material3 = { group = "androidx.media3", name = "media3-ui-compose-material3", version.ref = "media3" }
```

在 `app/build.gradle.kts` 添加三个 `implementation`，不迁移现有 Coil 2。

- [ ] **Step 4: 实现独立图片、尺寸缓存和占位**

`RichBlockImage` 使用 `SubcomposeAsyncImage`。初始宽高比依次取 HTML 尺寸、主题级缓存、16:9 默认值；成功时写入缓存。加载中与加载失败复用现有占位，成功后点击调用 `onImageClick(image.url)`。

- [ ] **Step 5: 实现按点击创建的 Media3 Compose 播放器**

初始只显示封面/播放占位。点击后 `remember(videoUrl)` 创建 `ExoPlayer`，设置 `MediaItem`、`prepare()` 并将 `playWhenReady=true`；使用 `androidx.media3.ui.compose.material3.Player` 显示官方控件。`DisposableEffect` 负责释放，生命周期 `ON_STOP` 负责暂停。错误状态提供重试和浏览器打开入口。

- [ ] **Step 6: 实现 iframe 和不支持媒体占位**

占位显示“暂不支持此嵌入内容”、可用域名与“打开网页”；有安全 URL 时整卡可点击，无 URL 时禁用点击。所有文案进入 `strings.xml`。

- [ ] **Step 7: 运行媒体测试和 Debug 构建**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContentMediaTest"
.\gradlew.bat :app:assembleDebug
```

Expected: 两条命令均 BUILD SUCCESSFUL。

---

### Task 4: 实现支持合并单元格的原生表格

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/RichContentTable.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/RichContentTableTest.kt`

**Interfaces:**
- Consumes: `RichContentBlock.Table`、`RichContentBlockView`
- Produces: `RichTableCellPlacement`、`buildRichTableLayout(rows)`、`resolveRichTableRowHeights(...)`、`RichContentTable`

- [ ] **Step 1: 写入网格占位与跨行高度分配失败测试**

```kotlin
@Test
fun resolveGrid_skipsColumnsOccupiedByRowSpan() {
    val table = tableOf(
        row(cell("A", rowSpan = 2), cell("B")),
        row(cell("C")),
    )

    val grid = resolveRichTableGrid(table)

    assertThat(grid.cells.map { it.row to it.column })
        .containsExactly(0 to 0, 0 to 1, 1 to 1).inOrder()
}

@Test
fun rowHeights_distributesExtraHeightAcrossSpannedRows() {
    val heights = calculateRichTableRowHeights(
        rowCount = 2,
        cells = listOf(
            RichMeasuredTableCell(row = 0, rowSpan = 2, heightPx = 100),
            RichMeasuredTableCell(row = 0, rowSpan = 1, heightPx = 30),
            RichMeasuredTableCell(row = 1, rowSpan = 1, heightPx = 30),
        ),
    )

    assertThat(heights.sum()).isAtLeast(100)
}
```

- [ ] **Step 2: 运行表格测试并确认失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContentTableTest"
```

Expected: FAIL，提示表格网格算法尚未定义。

- [ ] **Step 3: 实现纯函数网格解析和行高计算**

在测试文件内提供 `tableOf`、`row`、`cell` 三个最小构造辅助函数。`resolveRichTableGrid` 使用占用矩阵为每个单元格分配 `row/column`，并将非法 `colSpan/rowSpan` 限制为 `1..100`。`calculateRichTableRowHeights` 先处理普通单元格，再把跨行单元格缺少的高度平均分配到覆盖行。

- [ ] **Step 4: 使用 `SubcomposeLayout` 实现单份内容测量与独立背景网格**

按固定跨列宽度测量唯一一份单元格内容，计算各行高度后以单独的 Canvas 绘制覆盖完整跨行区域的背景和边框，避免为了二次测量组合两份单元格内容。外层 `BoxWithConstraints` 计算 `max(可用宽度, 列数 × 128.dp)`，并使用 `horizontalScroll` 承载超宽表格。表头使用 `surfaceVariant`，普通单元格使用表面背景，边框颜色来自 `outlineVariant`。

- [ ] **Step 5: 运行表格测试和相关组件测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContentTableTest"
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContent*Test"
```

Expected: BUILD SUCCESSFUL。

---

### Task 5: 接入主题正文与附言并删除 WebView 管线

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`
- Delete: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/RichHtmlText.kt`
- Delete: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/V2exHtmlDocument.kt`
- Delete: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/RichHtmlTextTest.kt`
- Delete: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/V2exHtmlDocumentTest.kt`
- Modify: `docs/subsystems/content-rendering.md`

**Interfaces:**
- Consumes: `RichContentParser.parse`、`RichContentBlockView`、`rememberRichContentImageSizeCache`
- Produces: 主题正文按块进入 `LazyColumn`，附言卡片调用原生渲染器，无 `RichHtmlText` 调用方

- [ ] **Step 1: 在主题详情层解析正文和附言文档**

在 `TopicDetailBody` 的 `LazyColumn` 之前，按 HTML、主题 ID 与自定义图床集合 `remember`：

```kotlin
val customImageHosts = LocalCustomImageHosts.current
val contentDocument = remember(detail.contentRendered, customImageHosts) {
    RichContentParser.parse(detail.contentRendered, customImageHosts)
}
val appendDocuments = remember(detail.appends, customImageHosts) {
    detail.appends.associate { it.index to RichContentParser.parse(it.contentRendered, customImageHosts) }
}
val imageSizeCache = rememberRichContentImageSizeCache(detail.topic.id)
```

- [ ] **Step 2: 拆分头部并将正文块摊平为稳定 Lazy 条目**

标题与元信息保留 `topic-detail-header`；正文使用：

```kotlin
itemsIndexed(
    items = contentDocument.blocks,
    key = { index, _ -> "topic-content-$index" },
    contentType = { _, block -> "topic-content-${block::class.simpleName}" },
) { _, block ->
    RichContentBlockView(
        block = block,
        modifier = Modifier.padding(horizontal = 16.dp),
        onImageClick = onImageClick,
        onUrlClick = openV2exUrl,
        imageSizeCache = imageSizeCache,
    )
}
```

附言卡片保留原有标题、时间和表面样式，内部遍历对应 `RichContentDocument.blocks`。回复摘要与回复索引相应改为通过稳定 key 定位，不能依赖固定 `index + 1`；回复引用滚动应先通过 key/当前条目偏移计算正确目标。

- [ ] **Step 3: 删除正文 WebView 实现与过期测试**

确认 `rg "RichHtmlText|RichHtmlLayoutCache|buildV2exHtmlDocument" app/src` 只剩历史文档后，删除两个实现文件和对应测试。保留 `V2exContentLinkify`，因为回复与新解析器共用。

- [ ] **Step 4: 更新内容渲染专题文档**

记录：正文/附言为 Jsoup → 原生模型 → Compose；回复/通知仍为 `HtmlText`；表格横向滚动；视频 Media3；iframe 占位；不再存在正文 WebView 高度缓存和 JavaScript 桥接。

- [ ] **Step 5: 运行局部测试、全量单元测试和构建**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.RichContentParserTest"
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.RichContent*Test"
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Expected: 全部 BUILD SUCCESSFUL。

---

### Task 6: 模拟器回归与交付检查

**Files:**
- Verify: `app/build/outputs/apk/debug/app-debug.apk`
- Verify: `docs/plans/2026-09-05-native-topic-rich-content-design.md`
- Verify: `docs/plans/2026-09-05-native-topic-rich-content.md`

**Interfaces:**
- Consumes: Debug APK、已登录模拟器、目标主题 `https://www.v2ex.com/t/1239372`
- Produces: 滚动、富文本、图片、表格、视频和 iframe 的模拟器验证结论

- [ ] **Step 1: 安装并以深链打开目标主题**

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop app.mystery0.nodeflow
adb shell am start -W -n app.mystery0.nodeflow/.MainActivity -a android.intent.action.VIEW -d "https://www.v2ex.com/t/1239372"
```

- [ ] **Step 2: 执行原始高频闪烁回归路径**

正文滚动到回复列表，再返回正文，重复进入回复至少三次。确认正文无黑屏/闪烁、没有正文内部纵向滚动、正文末尾与回复摘要都可到达。

- [ ] **Step 3: 验证富文本与媒体交互**

检查标题、粗体、链接、代码、引用、列表、图片加载/失败占位/大图预览、表格横向滚动、视频手动播放/暂停，以及 iframe 占位打开网页。目标主题未覆盖的标签使用本地测试 fixture 或另一个公开主题验证，不能向 V2EX 写入测试数据。

- [ ] **Step 4: 运行 Lint 并检查最终差异**

```powershell
.\gradlew.bat :app:lintDebug
git diff --check
git status --short
rg -n "Cookie|Authorization|once=|token=" docs/plans app/src/test app/src/main
```

Expected: Lint 与差异检查通过；无敏感数据、无无关修改、无 WebView 正文调用。

- [ ] **Step 5: 向用户汇报，不提交 Git**

汇报实现范围、关键渲染行为、测试/构建/模拟器结果和剩余风险。等待用户明确要求后再提交或推送。
