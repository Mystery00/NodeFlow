# 节点详情屏蔽节点实施计划

> **执行要求：** 使用内联 TDD 按步骤实施；每个步骤使用复选框跟踪。

**目标：** 在节点详情页安全接入 V2EX 节点屏蔽接口，并保证真实账号写操作只在用户二次确认后发生。

**架构：** Parser 提取目标节点 action token，RemoteDataSource 执行页面校验和动态 GET，Repository/UseCase 暴露业务动作，ViewModel 管理状态，Compose 页面负责登录分流与确认交互。

**技术栈：** Kotlin、Coroutines/Flow、Retrofit/OkHttp、Jsoup、Koin、Jetpack Compose、Material 3、JUnit 4、Truth。

**设计：** `docs/plans/2026-08-14-node-blocking-design.md`

## 全局约束

- 与用户沟通、代码注释和文档使用中文，日志使用英文。
- 不记录 Cookie、`once`、响应正文或用户隐私数据。
- 自动化网络测试不得访问真实 V2EX；真实屏蔽操作仅在用户明确授权的节点上执行。
- 不修改 Room、DataStore、User-Agent、Cookie、Referer 或 Origin 的公共规则。

### 任务一：解析节点 action token

**文件：**

- 新建：`app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserNodeActionTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`

**接口：**

- 产出：`fun parseNodeActionOnce(nodeId: Long, html: String): String?`

- [x] 写入直接屏蔽链接、收藏链接、错误节点和无关 token 的失败测试。
- [x] 运行 Parser 局部测试并确认目标函数不存在。
- [x] 使用限定链接选择器实现最小解析逻辑。
- [x] 重跑 Parser 局部测试并确认通过。

### 任务二：远端与领域动作

**文件：**

- 修改：`app/src/test/java/app/mystery0/nodeflow/data/node/NodeRemoteDataSourceTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/data/node/NodeRemoteDataSource.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/data/DataSourceModule.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/domain/node/NodeRepository.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/data/node/NodeRepositoryImpl.kt`
- 新建：`app/src/main/java/app/mystery0/nodeflow/domain/node/BlockNodeUseCase.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/domain/DomainModule.kt`

**接口：**

- 产出：`suspend fun NodeRemoteDataSource.blockNode(name: String)`
- 产出：`suspend fun NodeRepository.blockNode(name: String): Result<Unit>`
- 产出：`suspend operator fun BlockNodeUseCase.invoke(name: String): Result<Unit>`

- [x] 写入成功请求、成功后首页重定向、访问挑战、普通 Cloudflare 主题、缺少 ID/token 和异常最终 URL 的失败测试。
- [x] 运行 RemoteDataSource 局部测试并确认接口或行为失败。
- [x] 实现受访问保护的节点页读取、动态写请求和写操作结果校验；允许已实测的成功首页重定向。
- [x] 接入 Repository、UseCase 与 Koin。
- [x] 重跑数据层及 Koin 局部测试并确认通过。

### 任务三：ViewModel 与 Compose 交互

**文件：**

- 新建：`app/src/test/java/app/mystery0/nodeflow/feature/node/NodeViewModelTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/node/NodeUiState.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/node/NodeUiEvent.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/node/NodeViewModel.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/node/NodeScreen.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowNavHost.kt`

**接口：**

- 消费：`BlockNodeUseCase(name)` 与 `ObserveAuthSessionUseCase()`。
- 产出：登录分流、确认对话框、加载/错误/成功状态及返回导航。

- [x] 写入登录态、成功、失败和状态消费的 ViewModel 失败测试。
- [x] 运行 ViewModel 局部测试并确认新状态或事件不存在。
- [x] 实现最小 UiState、UiEvent 和 ViewModel 状态转换。
- [x] 接入屏蔽入口、确认/结果对话框和登录导航。
- [x] 重跑 ViewModel、导航与 Koin 局部测试并确认通过。

### 任务四：文档、全量验证与提交

**文件：**

- 修改：`docs/subsystems/network-auth.md`
- 修改：`docs/subsystems/ui-navigation.md`
- 修改：`docs/index.md`

- [x] 更新网络、UI 专题文档和索引。
- [x] 运行节点相关测试、全部 JVM 单元测试、Debug 构建和 Lint。
- [x] 模拟器验证无登录会话时进入登录页且未发送写请求；2026-08-16 经用户授权，使用已登录会话对 `localllm` 完成解除屏蔽与重新屏蔽，确认成功后不再误报无权访问，最终状态为已屏蔽。
- [x] 检查差异、敏感信息和计划覆盖后创建独立提交。
