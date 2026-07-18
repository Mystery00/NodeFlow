# V2EX_Polish 用户标签数据格式调查

日期：2026-07-17
方式：使用模拟器上已登录账号的会话，实际抓取 v2ex.com 记事本页面验证（数据已脱敏）。

## 结论

V2EX_Polish 浏览器插件把「用户标签」等数据同步存储在登录用户记事本
（`/notes`）的一条普通 note 中，格式稳定、可被第三方客户端只读解析。

## 发现与读取路径

1. **列表页** `GET /notes`（需登录 Cookie）：每条 note 的列表项形如

   ```html
   <span class="bigger"><div class="note_item_icon_note"></div>
     <a href="/notes/67331" class="black">V2EX_Polish_settings{&#34;settings-sync&#34;:{...（截断）</a></span>
   ```

   插件数据 note 的识别方式：`a[href^=/notes/]` 的链接文本以字面前缀
   `V2EX_Polish_settings` 开头。链接文本是 note 内容的截断预览，不能直接用作数据。

2. **查看页** `GET /notes/{id}`：只渲染截断标题（`<h1>` 内约 60 字符），**不含完整内容**。

3. **编辑页** `GET /notes/edit/{id}`：`<textarea>` 中是完整原文（HTML 实体转义，
   需要 unescape）。这是唯一能拿到完整数据的入口。

   注意路径是 `/notes/edit/{id}`，不是 `/notes/{id}/edit`（后者 404）。

## 数据格式

note 原文 = 字面前缀 `V2EX_Polish_settings` + 紧跟一个 JSON 对象（无空格分隔）：

```json
{
  "settings-sync": { "version": 46, "lastSyncTime": 1752..., "lastCheckTime": 1752... },
  "options": { "...": "插件自身设置，与标签无关" },
  "member-tag": {
    "SomeUser": {
      "tags": ["标签内容"],
      "avatar": "https://cdn.v2ex.com/avatar/xxxx/xxxx/123456_large.png?m=..."
    },
    "AnotherUser": { "tags": ["标签一", "标签二"], "avatar": "..." }
  }
}
```

- `member-tag`：username → `{ tags: string[], avatar: string }`。username 为 V2EX
  显示用户名（保留原大小写；V2EX 用户名匹配应不区分大小写）。
- `tags` 为字符串数组，可多个，内容任意（含中文、可能含敏感词，仅本人可见）。
- `avatar` 是插件缓存的头像 URL，客户端展示标签时可忽略。
- `settings-sync.version` 每次同步自增；`options` 与标签功能无关，解析时忽略
  未知字段即可。
- 实测账号中 `member-tag` 含 17 个条目，note 全文约 2.6KB；该 note 由插件
  高频编辑（46 次编辑）。

## 对客户端实现的约束

- 必须携带登录 Cookie；未登录会 302 到登录页，需先过页面分类，不能把登录页
  当业务内容解析（遵循现有 `V2exHtmlPageClassifier` 流程）。
- 用户可能没有安装插件或没有同步过：/notes 中不存在前缀匹配的 note，视为无标签。
- 用户可能有多条 note：应遍历列表找前缀命中的那条；理论上只有一条。
- note 内容由插件写入，解析必须容错（前缀后非法 JSON、缺 `member-tag` 键等
  都应降级为空数据，不能报错崩溃）。
- 本调查中的会话提取仅用于验证，Cookie 与标签明细不入库、不入文档。

## 写回验证（2026-07-18 补充）

- **编辑表单**：`POST /notes/edit/{id}`，表单仅一个字段 `content`，无 once/CSRF token；
  **新建表单**：`POST /notes/new`，同样仅 `content`。提交成功后 302 回记事页。
- 在真实账号上完成一次「添加标签 → 校验 → 删除标签」往返（app 内读-改-写路径）：
  - 添加后：顶层键与键顺序不变，`settings-sync`、`options` 与既有 `member-tag`
    条目逐项一致，仅新增目标用户条目（`tags` + `avatar`）。
  - 删除后：note 全文与编辑前**逐字节一致**（插件与本实现的 JSON 序列化均为
    紧凑格式且保留键顺序），确认写路径无损。
- 写回实现约束：保存时实时拉取最新内容做补丁，前缀/JSON 校验失败一律中止不提交；
  提交后回读编辑页校验目标标签生效。

## 同步协议（2026-07-18 补充，依据插件源码 coolpace/V2EX_Polish）

- 插件 `setV2P_Settings`（src/utils.ts）：每次备份写入
  `settings-sync = { version: 远端 version + 1, lastSyncTime: Date.now() }`（毫秒），
  初始化时 version=1；表单额外携带 `syntax=0`。
- 插件 popup 的同步判断：`本地 version >= 远端 version` 时只提供「开始备份」
  （本地覆盖远端）；仅当 `远端 version > 本地` 时提示「远程备份的版本较新」并提供
  「同步至本地」。**因此第三方写回必须递增 version，否则修改不会被插件拉取，
  且会在插件下一次备份时被本地旧数据覆盖。**
- 插件 `isValidSettings` 仅校验设置对象中存在 `options` 键；第三方新建记事时
  必须包含 `options`（可为空对象），否则被视为无效远端数据。
- 本客户端写回时：version +1（缺失按 0 起步）、lastSyncTime 刷新，
  settings-sync 内其他字段（如 lastCheckTime）原样保留。因此写回后内容必然与
  写回前不同，早先「删除后逐字节一致」的验证结论仅适用于不动 settings-sync 的旧实现。
- 实测（真实账号）：47 → 添加标签 → 48 → 删除标签 → 49，逐次 +1，
  options 与 settings-sync 未知字段均保留。
