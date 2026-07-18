# V2EX 创建回复设计

## 背景

NodeFlow 已具备主题正文、分页回复、回复引用预览、登录会话和受限页面识别能力，但尚不能主动回复主题。现有 `feature/editor` 只是发帖/回复共用编辑器占位；V2EX 实际只允许主题使用 Markdown，回复只能使用原生纯文本格式，因此回复与未来发帖不能共用内容编辑器。

本设计先实现创建回复。未来发帖使用独立的 `TopicEditor`，只复用图片上传领域接口，不复用回复编辑状态、格式规则或界面。

## 已验证的 V2EX 行为

### 回复表单

2026-07-18 使用已登录会话只读检查真实主题页，确认回复表单当前为：

```html
<form method="post" action="/t/<topicId>">
  <textarea name="content" maxlength="10000" id="reply_content"></textarea>
  <input type="hidden" name="once" value="<redacted>">
  <input type="hidden" name="return_to_page" value="<page>">
</form>
```

回复不支持 Markdown。V2EX 原生格式会自动展开 `i.v2ex.co` 图片直链；主题发帖才提供原生格式与 Markdown 两种语法。参见 [V2EX Markdown 帮助](https://www.v2ex.com/help/markdown)。

### V2EX 图库

官方说明与真实页面共同确认：

- 单文件最大 6 MB，当前页面支持 PNG、JPG、GIF、WebP。
- 原图公开存储并允许外链，不适合私密或敏感图片。
- 图库只对具备资格的账号开放，上传数量受账号额度约束。
- 上传会消耗铜币；2026-07-18 的单次样本消耗 27 铜币，不能据此写死价格。
- 图库详情页与 CDN 原图的稳定映射为 `www.v2ex.com/i/<filename>` → `i.v2ex.co/<filename>`。

参见 [图库介绍](https://v2ex.com/i/about)、[图库帮助](https://v2ex.com/help/i) 和 [站长对永久映射的说明](https://www.v2ex.com/t/1072027)。

一次经用户授权的上传/删除调查确认上传协议为：

```http
POST /i/upload
Accept: application/json
X-Requested-With: XMLHttpRequest
Referer: https://v2ex.com/i/upload
Content-Type: multipart/form-data

qqfile=<image>
```

成功响应示例：

```json
{
  "name": "<image-id>",
  "success": "true",
  "uri": "<image-id>.png",
  "url_o": "//i.v2ex.co/<image-id>.png",
  "message": "图片已经成功保存",
  "url_b": "//i.v2ex.co/<image-id>b.png"
}
```

`success` 当前是字符串而非 Boolean。上传不携带 `once`。删除入口为带当前页面 `once` 的 GET 导航；测试图片删除后图库条目消失、详情页提示不存在，CDN 原图返回 HTTP 404。创建回复首版不提供远端图片删除，也不自动删除未引用图片。

## 目标

- 在主题详情右下角提供随滚动显示/隐藏的悬浮回复按钮。
- 支持回复整个主题和回复指定楼层。
- 使用非模态 Bottom Sheet 编辑纯文本回复，并允许帖子详情在编辑时继续滚动。
- 从系统图片选择器选图，上传到当前 V2EX 账号的图库，并在光标处插入原图直链。
- 按主题持久化草稿，应用重启后恢复正文、选区和已上传图片信息。
- 每次提交前读取最新回复表单，可靠区分成功、失败、登录失效和结果无法确认。
- 提交成功后刷新详情并定位、高亮新楼层。

## 非目标

- 发帖及 Markdown 编辑器。
- “感谢”接口；首版只保留禁用入口。
- 回复编辑、删除或撤回。
- 自定义图床配置。
- V2EX 图库浏览和远端图片删除。
- 一次选择多张图片。
- 自动删除未引用的远端图片。
- 将 Markdown 转换为 V2EX 原生回复格式。

## 架构与组件边界

现有 `feature/editor` 占位替换为职责明确的 `feature/replyeditor`。回复主链路为：

```text
TopicDetailScreen
  → ReplyEditorUiEvent
  → ReplyEditorViewModel
  → CreateReplyUseCase
  → ReplyRepository
  → ReplyRemoteDataSource
  → V2EX 主题回复表单
```

图片上传独立为可扩展能力：

```text
系统图片选择器
  → UploadImageUseCase
  → ImageUploadRepository
  → V2exImageRemoteDataSource
  → POST /i/upload
```

首版只有 V2EX 图库实现。未来发帖或其他图床可以复用 `ImageUploadRepository`，但不引入通用编辑器状态。

草稿链路为：

```text
ReplyEditorViewModel
  → ReplyDraftRepository
  → ReplyDraftLocalDataSource
  → Room
```

`V2exHtmlParser` 负责回复表单、图库页面和结构化错误解析；RemoteDataSource 负责最终 URL 分类、请求执行和响应组合，不把 DOM 规则散落到 ViewModel 或 UI。

## 详情页交互

### 悬浮回复按钮

- 主题详情右下角显示回复 FAB。
- 列表向下滚动时通过位移、淡出和缩放动画隐藏；向上滚动时反向动画显示。
- 回复编辑 Sheet 展开时 FAB 隐藏；该状态下无需继续响应滚动显隐。
- 点击 FAB 展开主题回复编辑器，不插入楼层引用。

### 回复项操作

非编辑回复状态下，每条回复右上角显示三点图标。点击后打开操作 `ModalBottomSheet`：

- “回复”：关闭操作 Sheet，展开回复编辑 Sheet，并插入楼层引用。
- “感谢 · 暂未开放”：可见但禁用，不执行网络操作。

编辑回复状态下，每条回复右上角改为直接回复按钮。点击后不改变帖子滚动位置，直接在当前选区插入：

```text
@用户名 #楼层<空格>
```

插入后恢复输入焦点，并将光标放到引用之后。相同引用已紧邻当前光标时不重复插入。

## 回复编辑 Bottom Sheet

回复编辑器使用无遮罩、允许背景交互的持久式 Bottom Sheet，不使用会拦截背景触摸的 `ModalBottomSheet`。

- 初始高度约为 5 行正文。
- 内容增多时自动向上扩展。
- 最大高度为屏幕可用高度的 50%；达到上限后输入框内部滚动。
- 正确处理系统栏和 IME Insets。
- Sheet 外的帖子详情在编辑期间仍可滚动。
- 返回键优先关闭键盘，再关闭回复 Sheet；关闭不清除草稿。
- 包含纯文本输入、字符数、图片、清空、发布和上传/提交状态。
- 正文上限使用表单当前声明值，缺失时以 10,000 为兜底。

未登录时仍允许编辑和保存文字草稿，但图片上传与发布引导登录。登录返回后继续使用原草稿。

## 楼层引用与草稿合并

- 一个主题只维护一份回复草稿。
- 空草稿回复指定楼层时，在当前选区插入引用；默认选区为正文起始位置。
- 已有草稿不被覆盖，楼层引用插入到持久化恢复后的当前选区。
- 回复主题不会移除已有楼层引用。
- 引用只是 V2EX 正文语义，不存在独立的服务端楼中楼接口。

## 图片上传交互

1. 记录当前选区。
2. 使用 Android 系统图片选择器选择一张图片。
3. 本地校验 MIME、扩展名和 6 MB 限制。
4. 上传期间暂时锁定编辑区和发布按钮，保证插入位置不漂移。
5. 上传成功后使用 `url_o`，补全为 HTTPS，并在原选区插入独立一行图片直链。
6. 上传失败时不改变正文，不自动重试。
7. 上传结果无法确认时提示用户检查 V2EX 图片库，并提供打开图库的操作。

已上传图片可以在编辑器中预览。删除正文中的 URL 只移除回复引用，不删除图库文件，也不会退还铜币。

## 草稿持久化

Room 数据库从版本 3 升级到版本 4，提供 `Migration(3, 4)` 并更新 schema，禁止破坏性迁移。

```text
reply_drafts
├─ topic_id 主键
├─ content
├─ selection_start
├─ selection_end
└─ updated_at

reply_draft_images
├─ id 主键
├─ topic_id 外键
├─ remote_image_id
├─ original_url
├─ detail_url
├─ original_file_name
└─ created_at
```

正文或选区变化后防抖写入；进入后台和关闭 Sheet 时立即补写。删除本地草稿时级联删除图片记录，但不删除远端图片。

草稿只在以下情况清除：

- 回复提交已被可靠确认成功。
- 用户确认执行“清空草稿”。

网络错误、登录失效、上传失败和结果无法确认均保留草稿。

## 回复提交与成功确认

1. 校验正文非空且不超过当前上限；提交原始正文，不因校验而裁剪内容。
2. 锁定发布操作，阻止重复点击。
3. 重新请求 `GET /t/{topicId}` 获取最新表单。
4. 检查最终 URL、登录页、受限页、Cloudflare 和异常 HTML。
5. 解析表单 action、正文参数名和全部隐藏字段。
6. 使用现有 Cookie、正确的 `Origin`/`Referer` 执行一次 POST。
7. POST 不自动重试。

最终跳回当前主题时初步判定成功。若响应不明确，则加载最后一页回复，按当前用户名和标准化正文查找新回复：

- 找到：确认成功并返回楼层。
- 未找到：返回“提交结果无法确认”，保留草稿并禁止自动重试。

服务端结构化错误、登录失效、主题关闭和 Anti-Flood 分别映射为明确业务错误，不把底层异常原文直接交给 UI。

成功后清除草稿、强制刷新主题详情并定位高亮新楼层；无法取得楼层时滚动到回复列表底部。

## 错误与隐私

UI 至少区分：

- 需要登录或会话过期。
- 主题不存在、关闭或禁止回复。
- 正文为空或过长。
- Anti-Flood 或服务端拒绝。
- 图库权限不足或额度不足。
- 图片格式不支持或超过 6 MB。
- 普通网络错误。
- 图片上传结果无法确认。
- 回复提交结果无法确认。

失败不关闭回复 Sheet，也不清空正文。

日志使用英文，只记录 `topicId`、操作阶段、HTTP 状态和错误分类；不记录 Cookie、`once`、用户名、回复正文、图片内容、完整图片 URL 或完整响应。`once` 只存在于单次 RemoteDataSource 调用栈中。

## 测试设计

### Parser 与网络

- 解析回复表单 action、正文名、`maxlength` 和隐藏字段。
- 登录页、受限页和 Cloudflare 页面不能解析为回复表单。
- 图库介绍页、权限页和上传页正确分类。
- 图片上传成功字符串、协议相对 URL 和失败响应正确解析。
- MockWebServer 验证 POST 前必定重新 GET 表单。
- 验证动态隐藏字段、Cookie、Referer、Origin 和 multipart `qqfile`。
- 验证写请求不自动重试。
- 覆盖成功重定向、会话过期、Anti-Flood、额度不足和结果无法确认。

### Room

- `3 → 4` Migration 保留现有数据。
- 草稿新增、更新、恢复和删除。
- 图片记录随本地草稿级联删除。
- 不同主题的草稿互不影响。

### ViewModel 与纯函数

- 空草稿和已有草稿在当前选区插入楼层引用。
- 相邻位置相同引用不重复插入。
- 图片 URL 插入位置正确。
- 上传期间禁止发布。
- 失败保留正文，成功清除草稿。
- 滚动方向映射到 FAB 可见性。
- 编辑状态下三点图标切换为回复按钮。
- Sheet 不低于 5 行且不超过可用屏幕高度的 50%。

### 模拟器与真实写入

- 验证 FAB 动画、背景滚动、Sheet 自动增高、IME、系统栏、横竖屏和字体放大。
- 验证深色模式、Dynamic Color、无障碍语义和点击区域。
- 验证进程终止后的草稿恢复与登录恢复。
- 自动化网络测试只使用 MockWebServer，不访问真实 V2EX。
- 真实图片上传和真实回复会产生外部写入及铜币消耗；只在用户再次明确授权后执行。
- 真实回复仅使用用户指定主题，优先选择 V2EX sandbox 内容。

## 验收标准

- 回复 FAB 随详情列表滚动方向平滑显示和隐藏。
- 回复 Sheet 打开时详情列表仍可滚动，Sheet 高度从 5 行增长到最多半屏。
- 主题回复与指定楼层回复都能进入同一主题草稿；楼层引用插入当前光标处。
- 图片成功上传到 V2EX 图库并在当前位置插入可外链原图 URL。
- 应用重启后草稿、选区和图片记录能够恢复。
- 写请求没有自动重试，失败或结果无法确认时正文不会丢失。
- 成功后详情刷新并定位新楼层。
- Migration、相关局部测试、全量单元测试和 Debug 构建通过。
- 模拟器完成布局、动画、输入法、主题和进程恢复验证；真实回复测试由用户指定目标后执行。

## 导航栏安全区修正

回复 Sheet 的背景必须一直延伸到屏幕物理底边，系统导航栏安全区不能透出帖子详情。实现上由 `Surface` 负责覆盖完整底部区域，只把 `navigationBarsPadding()` 应用到 Sheet 内部内容；保持顶部圆角、底部直角，不修改应用或系统导航栏的全局颜色。手势导航和三键导航下都应满足这一规则。
