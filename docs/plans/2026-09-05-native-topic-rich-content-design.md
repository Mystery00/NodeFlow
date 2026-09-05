# 主题正文原生富文本渲染设计

## 背景

主题详情当前使用 `RichHtmlText` 将正文与附言放入自适应高度的 WebView。超长正文在滚动离屏并重新进入视口时，会触发超高 WebView 的图层合成异常，表现为正文区域高频闪烁。将 WebView 限制为固定视口虽然能规避闪烁，却会形成正文内部纵向滚动，与主题页滚动冲突，并可能让正文无法一次完整展示。

本设计将主题正文和附言迁移为原生 Compose 富文本。回复与通知继续使用现有 `HtmlText`，不纳入本次迁移。

## 目标

- 主题正文和附言完全脱离 WebView，不再依赖 JavaScript、高度测量或内部纵向滚动。
- 保留常用富文本语义，包括段落、标题、行内样式、链接、列表、引用、代码、表格、图片和视频。
- 段落内不同类型的行内内容连续排版，不因标签类型被强制换行。
- 正文内容参与主题页统一纵向滚动；超宽表格只在自身范围内横向滚动。
- 图片继续支持加载占位、失败重试和大图预览；视频使用官方 Media3 Compose 播放器。
- iframe 不执行，显示可点击占位内容并打开对应网页。
- 超长正文按块进入 `LazyColumn`，避免单个超高原生视图或图层。

## 非目标

- 不迁移回复、通知或其他 `HtmlText` 使用位置。
- 不实现浏览器级 CSS、JavaScript、iframe、表单、Canvas、SVG DOM 或任意网页布局。
- 不追求与 V2EX 网页逐像素一致；以内容结构、顺序、交互和可读性一致为准，并适配应用主题。
- 不允许正文自动播放视频或后台持续播放。

## 组件调研结论

现有 Compose 富文本组件均无法直接覆盖本次需求：

- Compose Rich Editor 支持 HTML、常用行内样式、列表、链接和实验性图片，但不支持表格和视频，且只读场景仍需引入完整编辑器模型。
- Compose Richtext 支持富文本 DSL、引用、代码、图片和表格，但没有 HTML 解析器，稳定版本仍处于 alpha，也不支持视频。
- HtmlConverterCompose、HtmlText 与 Compose 官方 `AnnotatedString.fromHtml` 适合基础文本，缺少表格、图片或视频中的多项能力。

因此不引入第三方通用富文本组件。NodeFlow 只实现 HTML 到原生组件的映射，文字测量与断行交给 Compose 文本引擎，图片交给 Coil，视频交给 Media3。

## 架构

数据流如下：

```text
正文 HTML
  → RichContentParser（Jsoup，纯 Kotlin）
  → RichDocument / RichBlock / RichInline
  → TopicRichContentRenderer（Compose）
  → Text / Coil Image / Native Table / Media3 Player / Placeholder
```

职责边界：

- `core.parser`：解析、URL 归一化、危险内容过滤、块级与行内语义分类。
- `core.model`：保存与 Compose 无关的不可变富文本模型。
- `core.designsystem.component`：将模型映射为 Material 3 / Compose UI。
- `feature.topicdetail`：把主题正文块摊平到现有 `LazyColumn`，提供链接、图片预览等回调。

解析器不依赖 Android 或 Compose，所有关键行为使用 JVM 单元测试覆盖。

## 内容模型

`RichDocument` 按 DOM 顺序保存 `RichBlock`：

- `Paragraph`：一个连续排版的行内内容序列，可带文本对齐方式。
- `Heading`：一至六级标题及其行内内容。
- `Quote`：可递归包含块级内容。
- `ListBlock`：有序或无序列表，支持起始序号与嵌套列表。
- `CodeBlock`：保留空格与换行的预格式文本。
- `Table`：标题、行、单元格、表头语义、`colspan`、`rowspan` 与单元格内容。
- `Image`：独立正文图片及尺寸、替代文本、链接信息。
- `Video`：播放地址、候选 MIME、封面、备用打开地址。
- `IframePlaceholder`：iframe 标题和原始 `src`。
- `Divider`：水平分隔线。

`RichInline` 用于一个段落或单元格内的连续内容：

- `Text`：文字及粗体、斜体、下划线、删除线、字号、颜色、背景色、上标、下标、行内代码和链接样式。
- `LineBreak`：显式 `<br>`。
- `InlineImage`：emoji、表情或其他明确的小尺寸图片。

同一段落的所有 `RichInline` 一次构建为 `AnnotatedString` 和 `InlineTextContent`，交给单个 Compose `Text` 排版。只有 HTML 块级边界、独立媒体和大图会产生新的纵向组件。

## HTML 解析规则

1. 使用 `Jsoup.parseBodyFragment`，基准地址为 `https://www.v2ex.com/`，所有相对 URL 转为绝对 URL。
2. 复用纯文本主题引用识别与自定义图床域名配置。
3. 严格保持 DOM 顺序；遇到独立图片、视频或其他块级节点时，只在真实边界刷新当前段落。
4. 支持 `p`、`div`、`section`、标题、`br`、`strong/b`、`em/i`、`u`、`del/s/strike`、`code/pre`、`blockquote`、`ul/ol/li`、`table/thead/tbody/tfoot/tr/th/td/caption`、`img`、`video/source`、`iframe` 与 `hr`。
5. 支持安全的行内样式子集：文字颜色、背景色、字号、字体粗细、字体样式、文字装饰和文本对齐。无法安全解析的样式忽略并继承应用主题。
6. `script`、`style`、`object`、`embed`、`form`、`input`、`button`、`canvas`、`noscript` 及其内容直接丢弃。
7. 未识别标签递归保留其可支持的子节点；不因未知包装标签丢失正文。
8. `embedded_image`、无行内小图证据的图片以及自定义图床链接生成独立图片块；emoji 类、明确小尺寸或短符号替代文本生成行内图片。
9. `<video>` 优先使用自身 `src`，否则按 DOM 顺序收集 `<source>`；播放器选择第一个可播放候选。无有效资源时降级为媒体占位。
10. `<iframe>` 永不载入；只接受 `http/https` 地址并生成占位。无安全地址时保留不可点击的“不支持的嵌入内容”提示。

## Compose 渲染

### 文本、列表、引用与代码

- 段落和标题使用 `Text`、`AnnotatedString` 与 `InlineTextContent`。
- 链接通过 `LinkAnnotation.Clickable` 调用现有 `onUrlClick`；无法应用内处理时交给系统浏览器。
- 列表标记与内容分列排版，嵌套层级增加缩进；列表项内部仍允许多个块。
- 引用使用主题色竖线、容器背景和递归内容。
- 行内代码使用等宽字体与圆角背景；代码块使用等宽字体并允许局部横向滚动，不产生纵向滚动。

### 图片

- 独立图片复用 Coil 2 与现有加载/错误占位，加载成功后点击进入 `ZoomableImageViewer`。
- 行内图片使用固定的 em 级占位，不因异步加载改变段落行高。
- 独立图片优先采用 HTML 提供的宽高比；未知尺寸先使用稳定默认比例，加载后记录到主题级尺寸缓存。
- 尺寸缓存按 URL 保存，图片离屏再进入时复用已知比例，避免重复高度跳变。
- 加载失败时点击重试；图片外层存在链接时提供打开原链接的辅助入口，不覆盖大图预览。

### 表格

- 表格使用原生 Compose 网格布局，保留表头、正文、`colspan` 和 `rowspan`。
- 列宽按内容和可用宽度计算并设置可读的最小宽度；总宽度超过屏幕时，表格整体横向滚动。
- 单元格高度按所在行内容计算，跨行单元格不足的高度分配到覆盖的行。
- 单元格内支持文本、行内图片、链接、列表、引用和代码；嵌套表格与视频降级为可点击占位，避免无限嵌套布局。

### 视频与 iframe

- 使用 Media3 1.11 的 `media3-exoplayer` 与 `media3-ui-compose-material3`。
- 视频初始显示封面或带播放按钮的 16:9 占位，用户点击后才创建并准备播放器。
- 同一正文允许多个视频，但只有用户主动播放的实例工作；离开组合或生命周期进入后台时暂停并释放。
- 播放失败时显示重试和“在浏览器中打开”入口。
- iframe 始终显示标题、域名和“打开网页”操作，不创建 WebView。

## 主题页滚动集成

当前标题、正文和附言位于同一个头部 `LazyColumn` 条目。迁移后调整为：

1. 标题和元信息保留独立头部条目。
2. 主题正文的每个顶层 `RichBlock` 使用稳定 key 作为独立条目，并统一应用正文水平边距和块间距。
3. 每条附言保留现有卡片外观，在卡片内部原生渲染其富文本；附言通常较短，不额外拆散卡片边界。
4. 回复摘要、回复条目和分页逻辑保持原有顺序与 key。

该结构让超长正文只组合当前视口附近的块，所有垂直手势都由主题页 `LazyColumn` 消费，不再存在正文高度探测、WebView 复用或嵌套纵向滚动。

## 错误处理与安全

- 解析器对单个异常节点局部降级，不因一个无效标签丢弃整篇正文。
- 非 `http/https` 的图片、视频和 iframe URL 不发起加载；普通链接额外允许现有安全路由支持的 scheme。
- 不执行 HTML 中的脚本、事件属性或远程样式，不记录正文 HTML、URL 查询参数或用户内容。
- 图片、视频和 iframe 分别使用明确的占位组件，保证失败内容仍可识别和操作。
- 富文本模型为空时不显示额外留白；解析整体失败时显示安全的纯文本降级内容。

## 测试与验证

### JVM 单元测试

- 行内标签嵌套保持在同一段落，不产生额外块。
- 文本、行内图片、独立图片和后续文本保持 DOM 顺序。
- 标题、引用、代码、嵌套列表与未知包装标签正确转换。
- 表格行列、表头、`colspan`、`rowspan` 和单元格富文本正确解析。
- 视频 `src/source/poster` 与 iframe URL 正确提取和过滤。
- 相对链接、纯文本主题引用、站内链接和自定义图床链接保持现有行为。
- 危险标签被移除，异常 HTML 局部降级。
- 图片尺寸分类和缓存策略保持稳定。

### 构建与模拟器

- 运行富文本相关局部测试、全部 `testDebugUnitTest` 和 `assembleDebug`。
- 在模拟器打开 `https://www.v2ex.com/t/1239372`，反复执行正文到回复、返回正文、再次进入回复的滚动路径，确认无闪烁、无内部纵向滚动、正文完整可见。
- 验证普通正文、附言、链接、大图预览、失败图片、表格横向滚动、视频播放/暂停和 iframe 占位。
- 检查浅色/深色主题、字体缩放和返回前台后的播放器状态。

## 迁移与清理

- 主题正文与附言切换到原生渲染后，删除无调用方的 `RichHtmlText`、WebView 高度缓存、JavaScript 桥接和相关测试。
- 删除只为 WebView 文档拼装存在的 `V2exHtmlDocument`；保留并复用仍被 `HtmlText` 与新解析器使用的链接识别能力。
- 更新内容渲染专题文档，明确正文/附言与回复采用不同渲染管线。

