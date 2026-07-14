# V2EX 链接应用内路由设计

日期：2026-07-12
状态：已确认

## 目标

1. 应用内（帖子正文、回复）点击 v2ex.com 链接时，先解析：能识别的链接直接在 app 内跳转到对应页面，无法识别才交给浏览器。
2. app 注册为 v2ex.com 链接的处理者，响应浏览器或其他 app 发来的链接点击。

## 识别范围

主机：`v2ex.com`、`www.v2ex.com`，协议 http/https。

| 链接形态 | 目标页面 | 说明 |
| --- | --- | --- |
| `/t/{数字id}` | 帖子详情 | 忽略 `?p=` 分页与 `#reply` 锚点 |
| `/go/{节点名}` | 节点详情 | |
| `/member/{用户名}` | 用户资料 | |
| 其他 | 浏览器 | 包括站点首页、设置页等 |

## 架构

### 1. 链接解析器 `core/link/V2exLinkParser.kt`

纯 Kotlin object，无 Android 依赖，可单测：

```kotlin
sealed interface V2exLink {
    data class Topic(val id: Long) : V2exLink
    data class Node(val name: String) : V2exLink
    data class Member(val username: String) : V2exLink
}

object V2exLinkParser {
    fun parse(url: String): V2exLink?
}
```

应用内点击与外部深链共用此解析器，规则只写一处。

### 2. 应用内点击接入

- `HtmlText`（回复，TextView 渲染）：已有 `onUrlClick: (String) -> Boolean` 钩子。在
  `ReplyItem` 现有楼层引用判断之后追加解析，识别成功调用导航回调并返回 true，
  否则返回 false 保持原有浏览器行为。
- `RichHtmlText`（帖子正文，WebView 渲染）：新增与 `HtmlText` 一致的
  `onUrlClick: (String) -> Boolean = { false }` 参数；`shouldOverrideUrlLoading`
  先询问该回调，返回 false 时才走现有 `openExternalUri`。
- 导航回调沿现有 `onTopicClick` / `onNodeClick` / `onUserClick` 模式从 NavHost
  传入各屏幕；帖子详情页补充"跳转另一个帖子"的回调。

### 3. 外部链接响应

- `MainActivity`：`launchMode="singleTask"`，新增 `VIEW + BROWSABLE` intent-filter
  （http/https；host：v2ex.com、www.v2ex.com；path：/t/、/go/、/member/ 前缀）。
- onCreate 与 onNewIntent 中用 `V2exLinkParser` 解析 `intent.data`，成功则通过
  NavHost 导航到目标页；冷启动时先落主页再推入目标页（返回键回主页）。

### 平台限制（已知且接受）

Android 12+ 的自动链接接管需要在 v2ex.com 部署 assetlinks.json 域名验证，
第三方客户端无法做到。因此外部链接默认仍开浏览器，用户需在
系统设置 → 应用 → NodeFlow → 默认打开 中手动添加链接一次。

## 测试

- `V2exLinkParser` 单测：三类链接、http/https、带分页/锚点/编码字符、
  尾部斜杠、非 v2ex 域名、畸形 URL、非数字帖子 id。
- 真机验证：帖子内点击 v2ex 帖子链接在 app 内打开；非 v2ex 链接开浏览器；
  `adb shell am start -a android.intent.action.VIEW -d "https://www.v2ex.com/t/xxx"`
  模拟外部链接三类路径。
