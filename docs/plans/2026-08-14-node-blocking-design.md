# 节点详情屏蔽节点设计

## 背景与接口调查

NodeFlow 的节点详情页目前只有固定到首页和刷新操作，没有 V2EX 账号级的节点屏蔽能力。参考 `D:/StudioProjects/v2er-fork` 当前源码：

- `APIs.ignoreNode` 请求 `GET /settings/ignore/node/{id}?once={once}`。
- `NodeTopicPresenter.ignoreNode` 使用节点 JSON 信息中的 ID，以及节点页面收藏链接携带的共享 `once`。
- 2026-08-16 使用已登录账号实测：请求生效后服务端最终重定向到 V2EX 首页，而不是节点页面。

节点屏蔽是会改变真实账号状态的写操作。首次实现时模拟器验收只打开确认对话框并取消；2026-08-16 经用户授权，使用 `localllm` 节点完成解除屏蔽和重新屏蔽的真实闭环验证，最终账号状态恢复为已屏蔽。

## 目标

- 节点详情页提供“屏蔽节点”入口。
- 未登录点击入口时进入现有登录页。
- 已登录点击后显示包含节点名称的二次确认；只有确认后才调用远端接口。
- 请求期间禁止重复提交；失败显示可理解的错误并允许重试。
- 成功后显示完成结果，由用户返回上一页。
- 不保存或打印 `once`、Cookie 和响应正文。

## 架构与数据流

```text
NodeScreen → NodeUiEvent.BlockNode → NodeViewModel → BlockNodeUseCase
→ NodeRepository.blockNode → NodeRemoteDataSource.blockNode
→ GET 节点页并提取 once → GET /settings/ignore/node/{id} → 结果状态 → NodeScreen
```

### Parser

`V2exHtmlParser.parseNodeActionOnce(nodeId, html)` 只检查与目标节点相关的以下链接：

- `/settings/ignore/node/{id}`
- `/settings/unignore/node/{id}`
- `/favorite/node/{id}`
- `/unfavorite/node/{id}`

这样既兼容 v2er 使用收藏链接取共享 token 的做法，也优先支持页面直接提供屏蔽链接；页面其他表单中的 `once` 不会被误用。

### 数据与领域层

`NodeRemoteDataSource` 复用 `V2exRawApi` 获取节点 ID和节点页，复用共享 Cookie 的 `V2exWriteApi.getHtml(url)` 执行动态 GET。节点页读取继续使用节点列表访问规则，跳转到首页视为无权访问；写操作结果使用独立访问目标，允许服务端成功后跳转到首页，但仍拒绝登录页、受限页、受限登录表单、结构化访问挑战页以及非 HTTPS、非 V2EX 域名或非标准端口的最终地址。访问挑战识别复用 Parser，并避免把首页普通主题中的 Cloudflare 文案当成挑战页。

`NodeRepository.blockNode(name)` 只返回 `Result<Unit>`，不缓存 `once`，不更改 Room 或 DataStore。新增 `BlockNodeUseCase` 表达业务动作。

### UI 与状态

`NodeViewModel` 观察现有登录会话并维护 `isBlockingNode`、`blockNodeError` 和 `blockNodeCompleted`。`NodeScreen` 在 TopAppBar 增加屏蔽图标：未登录调用现有登录导航，已登录打开确认对话框。成功状态展示完成对话框；用户点击“返回”后消费状态并退出节点页。取消确认不派发任何写事件。

## 错误处理

- 节点没有 ID：返回解析错误，不发送写请求。
- 节点页没有目标 action token：返回鉴权错误，提示刷新登录状态。
- 页面重定向到登录页或受限页：沿用访问保护错误。
- 写请求跳转到站外、非 HTTPS 或非标准端口：返回解析错误；成功后跳转到 V2EX 首页属于已验证的正常行为。
- 网络、HTTP 和协程取消沿用现有网络封装规则。

## 测试与验证

- Parser：直接屏蔽链接、收藏链接兼容、错误节点 ID、无关 token。
- RemoteDataSource：正确请求 ID/once/action URL；缺少 ID/token 时不写；首页和 action 地址结果成功，登录、受限、访问挑战与不可信最终 URL 不成功；首页主题提到 Cloudflare 时仍可正常完成。
- ViewModel：登录态、请求期间状态、成功、失败与消费事件。
- Koin：新增 UseCase 和构造参数可解析。
- 运行节点相关测试、全部 JVM 单元测试、Debug 构建和 Lint。
- 模拟器已使用 `localllm` 节点验证真实屏蔽成功、首页重定向不会再误报无权访问，并将账号状态恢复为已屏蔽。
