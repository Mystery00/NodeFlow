# 自定义图床域名设计

日期：2026-07-17
状态：已确认

## 背景与目标

V2EX 内容里只有带图片扩展名（`.jpg/.jpeg/.png/.webp/.gif`）的链接会被 app 当作图片渲染。
使用三方或自建图床的用户，其图片直链往往不带扩展名（如示例帖
[/t/1227803](https://www.v2ex.com/t/1227803) 中的 `https://imgur.com/a/lDQtGnP`），
在 app 里只能显示为普通链接，无法直接看图。

目标：在设置界面提供「自定义图床域名」配置；内容渲染时，命中配置域名的链接
按图片尝试加载，失败时回退到现有失败占位且原链接仍可点击。

## 需求确认结论

- **URL 处理**：仅域名匹配、原 URL 直接作为图片 src 加载；不做 URL 重写规则。
  imgur 相册页这类需要转换的链接会显示加载失败占位，属预期行为。
- **生效范围**：所有内容渲染处（主题正文 WebView、回复列表、通知中心等
  一切使用 `HtmlText` / `RichHtmlText` 的地方），行为一致，支持点击大图预览。
- **配置形态**：纯域名列表（添加/删除），无预设、无总开关（列表为空即关闭）。

## 总体方案

渲染层识别 + 设置注入：「链接是否是图片」的判定扩展为
「扩展名白名单 ∪ 用户配置域名」，判定只发生在现有两条渲染管线中；
域名列表存入现有 `SettingsStore`（DataStore），经 CompositionLocal 在应用根部
注入渲染组件。数据层（parser、Room 缓存）零改动，设置变更后重新组合即时生效。

已否决的替代方案：

- 解析层改写 HTML：解析结果进 Room 缓存，设置变更后旧内容不更新，
  且让 parser 与用户设置耦合，违反分层。
- Coil 加载层拦截：链接根本没进入图片加载流程，解决不了问题。

## 数据模型与存储

- `core/model/AppSettings` 新增 `customImageHosts: List<String> = emptyList()`，
  保存归一化后的域名，按添加顺序排列。
- `SettingsStore` 新增 `stringPreferencesKey("custom_image_hosts")`，
  存换行分隔字符串（与现有简单键值风格一致），提供
  `setCustomImageHosts(hosts: List<String>)`；读取时过滤空行。
- `domain/settings`：复用 `ObserveSettingsUseCase` 读取；`UpdateSettingsUseCase`
  新增 `setCustomImageHosts(hosts: List<String>)` 方法（与现有 setThemeMode 等并列），
  `SettingsRepository` 接口与实现同步扩展。
- 域名归一化（添加时执行，纯函数）：去首尾空白、去 `http(s)://` 前缀、
  去路径/查询/片段、去端口、转小写；归一化后为空或含非法字符（空白、`/` 等）则拒绝。

## 匹配逻辑

新增 `core/link/ImageHostMatcher`（纯 Kotlin object，可单测）：

- 输入：URL 字符串 + 配置域名集合；输出：是否按图片加载。
- 仅 http/https URL 参与匹配。
- 域名语义：配置 `example.com` 命中 `example.com` 与任意子域名
  （`img.example.com`）；配置 `img.example.com` 只命中该主机及其子域名，
  不反向命中 `example.com`。比较不区分大小写。
- 与现有能力的关系：带图片扩展名的 URL 仍走现有扩展名判定，本匹配器只补充
  「无扩展名但命中配置域名」的场景；v2ex.com 站内链接的路由行为不受影响。

## 渲染接入

- 新增 `LocalCustomImageHosts`（CompositionLocal，默认空集，放在
  `core/designsystem/component`，与消费它的渲染组件同层），在应用根部
  （现有提供主题/AppSettings 的位置）从设置流提供。
- **回复/通知（`HtmlText`）**：`extractHtmlImageSpecs` 扩展——无 `<img>` 子元素、
  `href` 命中配置域名的 `<a>` 也生成 `HtmlImageSpec`（非 compact）。
  链接文本保留在正文中，图片渲染在下方；加载失败时用户仍可点原链接走浏览器。
- **主题正文（`RichHtmlText`/WebView）**：`buildV2exHtmlDocument` 扩展——命中的
  锚点后插入 `<img src="同 URL">`，由现有注入脚本接管占位、失败重试与
  点击大图预览；锚点本身保留可点击。
- 失败行为完全复用现有「图片加载失败，点击重试」占位，不新增样式。

## 设置界面

- `SettingsScreen` 新增「内容浏览」分组：「自定义图床域名」条目。
- 列表每行显示域名 + 删除按钮；「添加域名」按钮弹出 M3 AlertDialog，
  内含单行 TextField 与确认/取消。
- 输入归一化后为空 → Snackbar 提示非法；归一化后与已有条目重复 → 去重不追加。

## 测试与验证

单元测试：

- 域名归一化：前缀/路径/端口/大小写/空串/非法字符。
- `ImageHostMatcher`：命中自身、子域名、不反向命中父域名、大小写、非 http(s)、
  未配置时全部不命中。
- `extractHtmlImageSpecs`：命中配置域名的锚点生成图片 spec，未命中不生成，
  已带扩展名的行为不回归。
- `buildV2exHtmlDocument`：命中锚点后插入 `<img>`，锚点保留。
- `SettingsStore`：域名列表读写往返、空列表。

真机/模拟器验证：

- 设置中添加 `imgur.com`，打开示例帖 1227803：正文链接下出现图片位，
  该相册页链接预期显示加载失败占位，链接仍可点击跳转。
- 用真实直链图床域名验证加载成功与点击大图预览。
- 删除域名后内容恢复为普通链接。
