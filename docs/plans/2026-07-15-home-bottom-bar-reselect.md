# 首页底部导航重复点击实施计划

> **执行要求：** 在当前会话按 TDD 执行；未经用户要求不创建 Git 提交。

**目标：** 已位于首页时重复点击首页底部导航项，滚动主题列表到顶部并刷新一次。

**架构：** `MainShell` 用纯函数区分普通首页导航与首页重复点击，并通过无 replay 通道向 `HomeScreen` 发送一次性请求。`HomeScreen` 持有 `LazyListState`，收到每个请求后立即复用 `HomeUiEvent.Refresh`，并独立启动滚动。

**技术栈：** Kotlin、Jetpack Compose、Navigation Compose、Paging Compose、JUnit 4、Truth。

## 全局约束

- 从其他底部页面进入首页时不自动刷新。
- 重复点击不向内部 NavHost 重复压入首页。
- 复用现有首页刷新事件和 Paging 数据流。

### 任务一：底部导航决策

**文件：**

- 修改：`app/src/test/java/app/mystery0/nodeflow/navigation/NavigationRouteTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/MainShell.kt`

- [x] 新增失败测试，验证首页重复点击返回 `ReselectHome`，其他路由点击首页返回 `NavigateHome`。
- [x] 运行 `NavigationRouteTest`，确认因决策类型或函数不存在而失败。
- [x] 实现 `HomeBottomBarAction` 与 `homeBottomBarAction(currentRoute)`。
- [x] 重跑局部测试并确认通过。

### 任务二：发送和消费重复点击请求

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/MainShell.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/home/HomeScreen.kt`

- [x] `MainShell` 当前在首页时发送无 replay 请求事件，否则执行现有顶层导航。
- [x] `HomeScreen` 收集请求事件并持有 `LazyListState`。
- [x] 每个请求立即发送 `HomeUiEvent.Refresh`，有内容时独立调用 `animateScrollToItem(0)`。
- [x] 将同一个 `LazyListState` 传给 `TopicList` 的 `LazyColumn`。

### 任务三：验证和文档

- [x] 运行导航局部测试、`:app:testDebugUnitTest` 和 `:app:assembleDebug`。
- [x] 更新 `docs/subsystems/ui-navigation.md` 与 `docs/index.md`。
- [x] 检查 diff、文档链接和未完成的人工 UI 验证。
