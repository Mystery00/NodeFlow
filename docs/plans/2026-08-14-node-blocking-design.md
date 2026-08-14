# 节点详情屏蔽节点设计

## 背景与接口调查

NodeFlow 的节点详情页目前只有固定到首页和刷新操作，没有 V2EX 账号级的节点屏蔽能力。参考 `D:/StudioProjects/v2er-fork` 当前源码：

- `APIs.ignoreNode` 请求 `GET /settings/ignore/node/{id}?once={once}`。
- `NodeTopicPresenter.ignoreNode` 使用节点 JSON 信息中的 ID，以及节点页面收藏链接携带的共享 `once`。
- 请求成功后服务端返回或重定向到节点页面。

节点屏蔽是会改变真实账号状态的写操作。实现与自动化测试完整覆盖请求，但模拟器验收只打开确认对话框并取消，不实际发送屏蔽请求。

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

`NodeRemoteDataSource` 复用 `V2exRawApi` 获取节点 ID和节点页，复用共享 Cookie 的 `V2exWriteApi.getHtml(url)` 执行动态 GET。节点页和操作结果都经过现有访问保护，登录页、受限页或首页重定向不能当作成功；操作完成后最终 URL 必须回到 `/go/` 节点路径。

`NodeRepository.blockNode(name)` 只返回 `Result<Unit>`，不缓存 `once`，不更改 Room 或 DataStore。新增 `BlockNodeUseCase` 表达业务动作。

### UI 与状态

`NodeViewModel` 观察现有登录会话并维护 `isBlockingNode`、`blockNodeError` 和 `blockNodeCompleted`。`NodeScreen` 在 TopAppBar 增加屏蔽图标：未登录调用现有登录导航，已登录打开确认对话框。成功状态展示完成对话框；用户点击“返回”后消费状态并退出节点页。取消确认不派发任何写事件。

## 错误处理

- 节点没有 ID：返回解析错误，不发送写请求。
- 节点页没有目标 action token：返回鉴权错误，提示刷新登录状态。
- 页面重定向到登录、受限页或首页：沿用访问保护错误。
- 写请求未回到节点路径：返回解析错误，避免把未知 HTML 视为成功。
- 网络、HTTP 和协程取消沿用现有网络封装规则。

## 测试与验证

- Parser：直接屏蔽链接、收藏链接兼容、错误节点 ID、无关 token。
- RemoteDataSource：正确请求 ID/once/action URL；缺少 ID/token 时不写；异常最终 URL 不成功。
- ViewModel：登录态、请求期间状态、成功、失败与消费事件。
- Koin：新增 UseCase 和构造参数可解析。
- 运行节点相关测试、全部 JVM 单元测试、Debug 构建和 Lint。
- 模拟器只验证入口、登录分支、确认对话框、取消操作和布局，不点击最终“屏蔽”。

