# HTML、链接、图片与内容渲染

## HTML 解析

- `core.parser` 负责将 V2EX HTML 转换为明确的数据模型。
- Parser 优先使用稳定的 id、class、属性和语义关系，避免绝对节点索引。
- 页面必须先经过访问状态分类，不能把登录页、受限页或 Cloudflare 页面当作业务内容。
- 新解析规则使用最小、脱敏 fixture 覆盖正常、缺失字段、空内容和异常页面。
- 主题列表置顶状态优先读取显式 class、data 属性或状态标签；当前 V2EX 无显式标记时，按最近的 `.box` 列表容器分别计算最长显示时间倒序子序列，只将 60 秒误差之外的最小异常集合识别为置顶，避免侧栏污染，也不因第一条、推广节点本身或被插入主题跨过而误标普通主题。

## 富文本与链接

- 主题正文与附言使用原生 Compose 富文本管线；回复与通知继续复用 `HtmlText`。
- 正文 HTML 先由 Jsoup 清洗并转换为 `RichContentDocument`，再按块进入主题页 `LazyColumn`；
  段落内的粗体、斜体、删除线、上下标、颜色和链接在同一个 Compose `Text` 中连续排版。
- 独立图片使用 Coil，并在主题生命周期内缓存成功加载后的宽高比，离屏重挂时不再回到默认高度；
  视频点击后使用 Media3 Compose `Player`，页面离开前台时暂停，组件销毁时释放播放器。
- 表格使用原生网格布局并支持 `rowspan`、`colspan`，宽度超出正文时仅表格横向滚动；
  iframe 不执行，显示可点击的网页占位卡片。
- `v2ex.com` 与 `www.v2ex.com` 的主题、节点和用户链接优先应用内导航，其他链接交给浏览器。
- 外部深链和内容点击共用 `V2exLinkParser`，相对 URL 基于正确的 V2EX 地址解析；
  站内内容里的相对路径锚点（如回复中的 `<a href="/t/1226857">`）同样按站内链接识别。
- 纯文本形式的主题引用（如 `/t/123`、`v2ex.com/t/123`）在渲染前由
  `linkifyV2exTopicReferences` 转为绝对地址链接，已有链接和代码块不做二次识别。
- 图床域名识别（`ImageHostMatcher` + `LocalCustomImageHosts`）：内置常用图床
  （`BUILT_IN_IMAGE_HOSTS`：i.imgur.com、i.v2ex.co）与用户在设置中配置的域名取并集；
  命中域名的链接在回复（`HtmlText`）与正文原生富文本中按图片尝试加载，
  失败回退现有占位，原链接保留可点击。
- Polish 用户标签（`PolishMemberTagParser` + `LocalMemberTags`）：从登录用户记事本中
  前缀为 `V2EX_Polish_settings` 的记事解析 `member-tag` 数据，缓存后在回复列表、
  帖子作者行与用户主页以 `MemberTagChip` 展示；匹配仅查本地缓存，不在浏览路径发起网络请求。
  用户主页提供「编辑标签」入口，保存时实时读-改-写记事本（`PolishMemberTagParser.patch`
  仅替换 member-tag 下目标用户条目，其他插件设置原样保留），与 V2EX_Polish 插件双向兼容。
- 不在可执行 WebView 环境中注入未经校验的任意 HTML 或脚本。

## 图片

- 普通图片加载复用 Coil，GIF 自动播放原始动画，SVG 使用现有 Coil 支持。
- 正文与回复大图预览复用 ZoomImage/`ZoomableImageViewer`。
- 图片分类优先使用服务端明确语义；emoji、图标和紧凑图片不能因通用规则被放大。
- 修改图片渲染时同时检查正文、回复、点击、加载失败和大图预览。

相关设计：

- [`../plans/2026-07-12-v2ex-link-routing-design.md`](../plans/2026-07-12-v2ex-link-routing-design.md)
- [`../plans/2026-07-14-v2ex-reply-embedded-image-design.md`](../plans/2026-07-14-v2ex-reply-embedded-image-design.md)
- [`../plans/2026-07-14-v2ex-reply-embedded-image.md`](../plans/2026-07-14-v2ex-reply-embedded-image.md)
- [`../plans/2026-09-05-native-topic-rich-content-design.md`](../plans/2026-09-05-native-topic-rich-content-design.md)

## 排障入口

内容错误时先保存脱敏最小 HTML，确认页面分类和 Parser 输出，再检查领域映射与渲染；链接异常检查解析器与导航回调；图片异常检查 HTML 语义分类、URL、Coil 状态、布局规格和预览入口。
