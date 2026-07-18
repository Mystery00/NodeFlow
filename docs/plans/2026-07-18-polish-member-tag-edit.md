# V2EX_Polish 用户标签编辑实施计划

对应设计：[2026-07-18-polish-member-tag-edit-design.md](2026-07-18-polish-member-tag-edit-design.md)

## Task 1：PolishMemberTagParser 补丁纯函数

- `patch(content, username, tags, avatarUrl = null): String?`：
  前缀校验 → JSON 解析为 `JsonObject` → 标签归一化（trim/去空/去重）→
  用户名大小写不敏感匹配已有条目（保留原键名与条目内未知字段，仅替换 `tags`；
  新条目附 `avatar`）→ 空标签移除整个条目 → 重建根对象保持键顺序 →
  序列化为 `NOTE_PREFIX + JSON`；任一环节失败返回 null。
- `buildInitial(username, tags, avatarUrl = null): String?`：
  构建仅含 `member-tag` 的最小记事内容；归一化后空标签返回 null。
- 测试：保留其他设置键与键顺序、保留条目未知字段与 avatar、新增/替换/删空、
  大小写匹配、非法前缀/非法 JSON 拒绝、产物可被 `parse` 回读。

## Task 2：写回接口与远端数据源

- `V2exRawApi` 新增：
  `@FormUrlEncoded @POST("notes/edit/{id}") noteEditSubmit(@Path id, @Field("content") content)`、
  `@FormUrlEncoded @POST("notes/new") noteNewSubmit(@Field("content") content)`。
- 同步修补所有实现 `V2exRawApi` 的测试 fake（沿用各文件既有辅助习惯）。
- `MemberTagRemoteDataSource.updateMemberTags(username, tags, avatarUrl): Map`：
  - 有记事：取编辑页内容 → `patch`（null 即中止，Parse 错误）→ 提交 →
    回读编辑页校验标签已生效 → 返回补丁后完整映射。
  - 无记事：`buildInitial`（null 即无事可做，返回空映射）→ `POST /notes/new` →
    重新定位记事并回读校验 → 返回映射。
  - 提交响应仅做登录页/受限页守卫（重定向终点在 /notes 下即可）。
- 测试：完整读改写序列、内容异常中止不提交、回读校验失败报错、新建路径。

## Task 3：仓库写路径与 UseCase

- `MemberTagRepository.setTagsForUser(username, tags, avatarUrl): Result<Unit>`；
  Impl 新增 `updateRemoteTags` lambda 依赖；未登录直接返回 Auth 失败；
  成功后将返回映射写缓存并更新 `syncedAt`。
- `UpdateMemberTagsForUserUseCase`；DomainModule、RepositoryModule 注册。
- 测试：成功写缓存、未登录快速失败、远端失败不动缓存。

## Task 4：用户主页编辑 UI

- `ProfileUiState`：`editableMemberTags`（未过滤缓存中该用户的标签）、
  `isTagDialogVisible`、`isSavingTags`、`tagEditError`。
- `ProfileUiEvent`：`EditMemberTags`、`DismissTagDialog`、`SaveMemberTags(tags)`。
- `ProfileViewModel`：新增 `ObserveMemberTagsUseCase`（供对话框初始值，
  不受展示开关影响）与 `UpdateMemberTagsForUserUseCase`；保存成功关闭对话框，
  失败在对话框内展示错误并保留输入；avatar 取当前 `user?.avatarUrl`。
- `ProfileScreen`：标签行旁「编辑标签」入口（无标签时同样可见）；
  编辑对话框：chip 可删除、输入框添加、保存/取消、保存中禁用。
- `FeatureModule` 更新 ProfileViewModel 注册。

## Task 5：全量验证、文档与提交

- `testDebugUnitTest` + `assembleDebug` + 模拟器实机：
  添加标签 → 三处展示位即时可见；回读记事确认其他设置键未被破坏；
  删除标签恢复原状；未登录/异常路径提示。
- 文档：content-rendering.md、storage.md 补充写路径说明；
  investigations 补充写回验证记录（脱敏）；index.md 挂链接。
- 分任务提交，最后 docs 提交收尾。
