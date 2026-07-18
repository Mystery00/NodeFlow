# V2EX_Polish 用户标签编辑设计

日期：2026-07-18
状态：已确认

## 背景与目标

在已有的 Polish 用户标签展示能力（见
[2026-07-17-polish-member-tag-design.md](2026-07-17-polish-member-tag-design.md)）基础上，
允许用户在 app 内为其他用户新增、修改、删除标签，并保持与 V2EX_Polish
浏览器插件的数据完全兼容：写回同一条记事本记事，插件与 app 双向可见。

## 已验证的前提（只读探测）

- 编辑记事：`POST /notes/edit/{id}`，表单仅一个字段 `content`，无 once/CSRF token。
- 新建记事：`POST /notes/new`，同样仅 `content` 字段。
- 记事完整内容仅在 `/notes/edit/{id}` 的 `textarea[name=content]` 中。

## 产品决策（用户已确认）

- 编辑入口仅放在用户主页（用户名/标签下方）。
- 账号没有 Polish 记事时自动 `POST /notes/new` 创建仅含 `member-tag` 的最小设置记事。

## 核心原则：保存时读-改-写，最小化修改

Polish 记事中存放插件的全部设置，不只是标签。写坏会毁掉用户的插件配置，因此：

1. 保存时实时拉取记事最新内容，不使用启动时的本地缓存作为写回基础。
2. 以 `JsonObject` 解析，仅替换 `member-tag` 键下对应用户名的条目；
   其他设置键、同条目内的未知字段全部原样保留（kotlinx `JsonObject` 保留键顺序）。
3. 多重防护：内容前缀校验失败、JSON 解析失败、补丁产物重新解析失败，任一环节
   不通过即中止，不提交任何内容。
4. 提交后回读编辑页校验内容生效，再把补丁后的完整标签表写入本地缓存并更新
   `syncedAt`，界面立即可见。

## 数据格式规则

- 条目结构 `{"tags": [...], "avatar": url}`：
  - 编辑已有用户：保留原 avatar 与条目内其他未知字段，仅替换 `tags`。
  - 新增用户：`tags` + 当前主页可得的头像 URL（可为空则省略 avatar）。
  - 标签删空：移除整个用户名条目（与插件行为一致）。
- 标签入库前 trim、去空、去重。
- 新建记事内容：`V2EX_Polish_settings{"member-tag":{...}}`。

## 架构落点

- `core.parser.PolishMemberTagParser`：新增纯函数
  `patch(content, username, tags, avatarUrl): String?`（失败返回 null）与
  `buildInitial(username, tags, avatarUrl): String`，承载全部 JSON 修改逻辑，全量单测。
- `core.network.V2exRawApi`：新增 `POST notes/edit/{id}` 与 `POST notes/new`
  两个 FormUrlEncoded 接口。
- `data.membertag.MemberTagRemoteDataSource`：新增
  `updateMemberTags(username, tags, avatarUrl): Map<String, List<String>>`，
  完成定位记事 → 取内容 → patch/buildInitial → 提交 → 回读校验的完整序列，
  复用现有登录/访问受限守卫。
- `domain.membertag`：`MemberTagRepository.setTagsForUser(...): Result<Unit>` 与
  `UpdateMemberTagsForUserUseCase`；Repository 成功后同步写本地缓存。
- `feature.profile`：主页头部「编辑标签」入口 + 编辑对话框
  （现有标签 chip 可删除、输入框添加、保存/取消、保存中禁用、失败保留输入）。
  对话框初始值取自未过滤的缓存标签流（不受展示开关影响），避免开关关闭时误清标签。

## 风险与兜底

- 新建的最小 JSON 与插件的兼容性：插件对缺失设置键使用默认值，风险低；
  实现后在数据格式调查文档补充验证记录。
- 与浏览器插件并发保存互相覆盖：与插件自身多标签页并发行为一致，可接受。
- 记事长度上限未知：提交失败时报错并保持远端现状，不做半量写入。
- 未登录：保存路径返回 Auth 错误，界面提示，不影响浏览。
