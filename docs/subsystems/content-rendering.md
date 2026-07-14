# HTML、链接、图片与内容渲染

## HTML 解析

- `core.parser` 负责将 V2EX HTML 转换为明确的数据模型。
- Parser 优先使用稳定的 id、class、属性和语义关系，避免绝对节点索引。
- 页面必须先经过访问状态分类，不能把登录页、受限页或 Cloudflare 页面当作业务内容。
- 新解析规则使用最小、脱敏 fixture 覆盖正常、缺失字段、空内容和异常页面。

## 富文本与链接

- 优先复用 `HtmlText`、`RichHtmlText`、`V2exHtmlDocument` 等现有渲染管线。
- `v2ex.com` 与 `www.v2ex.com` 的主题、节点和用户链接优先应用内导航，其他链接交给浏览器。
- 外部深链和内容点击共用 `V2exLinkParser`，相对 URL 基于正确的 V2EX 地址解析。
- 不在可执行 WebView 环境中注入未经校验的任意 HTML 或脚本。

## 图片

- 普通图片加载复用 Coil，SVG 使用现有 Coil 支持。
- 正文与回复大图预览复用 ZoomImage/`ZoomableImageViewer`。
- 图片分类优先使用服务端明确语义；emoji、图标和紧凑图片不能因通用规则被放大。
- 修改图片渲染时同时检查正文、回复、点击、加载失败和大图预览。

相关设计：

- [`../plans/2026-07-12-v2ex-link-routing-design.md`](../plans/2026-07-12-v2ex-link-routing-design.md)
- [`../plans/2026-07-14-v2ex-reply-embedded-image-design.md`](../plans/2026-07-14-v2ex-reply-embedded-image-design.md)
- [`../plans/2026-07-14-v2ex-reply-embedded-image.md`](../plans/2026-07-14-v2ex-reply-embedded-image.md)

## 排障入口

内容错误时先保存脱敏最小 HTML，确认页面分类和 Parser 输出，再检查领域映射与渲染；链接异常检查解析器与导航回调；图片异常检查 HTML 语义分类、URL、Coil 状态、布局规格和预览入口。
