# V2EX_Polish 用户标签兼容设计

日期：2026-07-17
状态：已确认

## 背景与目标

V2EX_Polish 浏览器插件支持给其他用户打标签，数据同步存储在登录用户记事本
（`/notes`）的一条 note 中。目标：app 只读解析这份数据，在浏览内容时展示
用户标签。数据格式与读取路径见
[调查记录](../investigations/2026-07-17-v2ex-polish-member-tag-format.md)。

## 核心约束（用户确认）

- **启动时后台拉取一次**标签数据，解析后**存入本地缓存**。
- 浏览帖子时**只与本地缓存匹配**展示，**帖子加载路径上绝不发起标签网络请求**。
- 展示位置：回复列表用户名旁、帖子作者信息行、用户主页，三处全部。
- app 只读展示，不提供编辑标签能力（编辑由插件负责）。

## 数据获取与解析

- `V2exRawApi` 新增：
  - `GET notes` → 记事本列表 HTML；
  - `GET notes/edit/{id}` → note 编辑页 HTML（textarea 内含完整原文）。
  - 走现有拦截器（Cookie/UA），已实测可用。
- `V2exHtmlParser` 新增：
  - `parsePolishNoteId(html): Long?`：在列表页找链接文本以
    `V2EX_Polish_settings` 开头的 `a[href^=/notes/]`，返回 note id；找不到返回 null。
  - `parseNoteEditContent(html): String?`：提取编辑页第一个 `<textarea>` 原文
    并做 HTML 实体解码。
  - 两者输入先过现有页面分类，登录页/受限页不当业务内容。
- 新增 `core/parser/PolishMemberTagParser`（纯 Kotlin，kotlinx.serialization）：
  - 输入 note 原文，校验字面前缀 `V2EX_Polish_settings`，剩余部分按 JSON 解析，
    取 `member-tag` 键 → `Map<String, List<String>>`（username → tags）。
  - 忽略 `settings-sync`、`options`、`avatar` 等无关字段（ignoreUnknownKeys）。
  - 任何异常（前缀不符、非法 JSON、缺键、类型不符）降级返回空映射，不抛错。

## 数据层与缓存

- `domain/membertag/MemberTagRepository`：
  - `observeTags(): Flow<Map<String, List<String>>>` —— 从本地缓存响应式读取。
  - `refresh(): Result<Unit>` —— 拉取 /notes → 定位 note → 拉编辑页 → 解析 →
    写缓存与同步时间戳。
- 实现 `data/membertag/MemberTagRepositoryImpl`；Koin 注册。
- 缓存介质：现有 DataStore 两个键——`polish_member_tags`（解析后 username→tags
  的 JSON 字符串）、`polish_member_tags_synced_at`（epoch 秒）。数据量小
  （几十条），不建 Room 表。
- 刷新策略：
  - 应用启动且已登录时，后台刷新一次（放在 AppViewModel 现有启动流程里，
    不阻塞 UI）；距上次同步不足 1 小时则跳过，避免频繁编辑记事本页拉取。
  - 设置页提供手动「同步」入口。
  - 刷新失败保留旧缓存；未登录不请求；登出时清空缓存。
- 未安装插件/无 note：refresh 成功但写入空映射。

## UI 展示

- 新增 `LocalMemberTags: ProvidableCompositionLocal<Map<String, List<String>>>`
  （默认空映射，放 `core/designsystem/component`），应用根部由 AppViewModel
  经 `ObserveMemberTagsUseCase` 注入——与 `LocalCustomImageHosts` 模式一致。
  开关关闭时根部直接注入空映射，展示侧无需感知开关。
- 新增标签徽标组件 `MemberTagChips(tags: List<String>)`（core/ui）：形态沿用
  「楼主」徽标（extraSmall Surface + labelSmall），配色改用
  `tertiaryContainer`/`onTertiaryContainer` 与楼主徽标区分；多标签并列可换行，
  单个标签超长省略。
- 用户名匹配不区分大小写（数据中保留原大小写）；提供纯函数
  `memberTagsFor(tags: Map<String, List<String>>, username: String): List<String>`。
- 接入三处：
  1. `ReplyItem`：用户名行，与「楼主」徽标并列（楼主徽标在前）；
  2. 帖子详情作者信息行（`TopicMetadataRow` 用户名旁）；
  3. 用户主页用户名区域下方。
- 设置开关关闭时三处均不展示。

## 设置

「内容浏览」分组新增「Polish 用户标签」条目：

- Switch 开关（默认开）：`AppSettings` 新增 `showMemberTags: Boolean = true`，
  经 `SettingsStore`（键 `polish_member_tags_enabled`）持久化，走现有
  Repository/UseCase 模式；关闭仅隐藏展示，不清缓存；
- 副文本显示上次同步时间（无记录显示「未同步」）；
- 「同步」按钮手动刷新，完成后 Snackbar 提示成功/失败。

## 错误处理

- 未登录、无插件 note、解析失败 → 空标签，界面无任何占位或错误提示
  （手动同步除外，手动操作有 Snackbar 反馈）。
- 拉取/解析在 IO 线程，失败只记英文日志，不影响启动与浏览。

## 测试与验证

单元测试：

- `PolishMemberTagParser`：正常数据、前缀不符、非法 JSON、缺 `member-tag`、
  空 tags 数组、含未知字段。
- `V2exHtmlParser.parsePolishNoteId`：命中、无命中、多条 note、登录页返回 null
  （脱敏最小 fixture）。
- `V2exHtmlParser.parseNoteEditContent`：正常 textarea、实体解码、登录页。
- Repository：刷新成功写缓存、失败保留旧值、未登录跳过、登出清空（fake 依赖）。
- `memberTagsFor`：大小写不敏感、未命中返回空。

模拟器验证（当前账号已有 17 个用户的标签）：

- 启动后打开有被打标签用户参与的帖子，回复行显示标签；
- 打开该用户主页显示标签；
- 设置中关闭开关后标签消失，开启恢复；手动同步提示成功；
- 浏览帖子过程中抓包/日志确认无 /notes 请求（仅启动与手动同步触发）。
