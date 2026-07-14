# 帖子详情节点 Chip 实施计划

> **执行要求：** 按 TDD 顺序逐项完成，不使用子 Agent；未经用户要求不创建 Git 提交。

**目标：** 在帖子详情元信息末尾展示可点击的节点 Chip，并跳转到对应节点详情页。

**架构：** 详情页复用 `topicNodeChip(Topic)` 生成 `TopicNodeChip`，复用 `NodeChip` 组件展示，点击调用现有 `onNodeClick(String)`。不修改 ViewModel、Repository、数据模型和导航图。

**技术栈：** Kotlin、Jetpack Compose、Material 3、JUnit 4、Truth。

## 全局约束

- 代码注释和文档使用中文，日志使用英文。
- 节点标题为空时回退节点名；节点名为空时不展示 Chip。
- 业务逻辑先写失败测试，再写最小实现。

### 任务一：详情节点内容映射

**文件：**

- 修改：`app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailMetadataTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`

- [x] 新增测试：`topicDetailNodeChip` 对有效节点返回列表一致的标题和导航名，对空节点名返回 `null`。
- [x] 运行 `TopicDetailMetadataTest`，确认因函数不存在而失败。
- [x] 实现 `internal fun topicDetailNodeChip(detail: TopicDetail): TopicNodeChip? = topicNodeChip(detail.topic)`。
- [x] 重跑 `TopicDetailMetadataTest`，确认通过。

### 任务二：元信息区域渲染与导航

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`

- [x] 为 `TopicMetadataRow` 增加 `onNodeClick` 参数，并从 `TopicDetailContent` 传入现有回调。
- [x] 使用 Row 展示元信息 Text 与 `NodeChip`，Chip 点击调用 `onNodeClick(chip.nodeName)`。
- [x] 保持元信息单行滚动，节点为空时不展示 Chip。
- [x] 运行局部测试、`:app:testDebugUnitTest` 和 `:app:assembleDebug`。

### 任务三：文档与差异检查

- [x] 更新 `docs/subsystems/ui-navigation.md`，记录详情页节点入口复用统一路由。
- [x] 检查 diff、文档链接和敏感信息，记录未完成的人工 UI 验证。
