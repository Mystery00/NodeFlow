# 首页重复点击展开 App Bar 实施计划

> **执行要求：** 在当前会话按 TDD 执行；未经用户要求不创建 Git 提交。

**目标：** 首页重复点击底部导航项时，同时展开 App Bar、滚动主题列表到顶部并刷新。

**架构：** 保留现有重复点击事件通道，在 `HomeScreen` 内统一协调 `TopAppBarState`、`LazyListState` 和刷新事件。用纯 Kotlin 决策函数覆盖界面动作选择，不修改导航层和数据层。

**技术栈：** Kotlin、Jetpack Compose、Material 3、Paging Compose、JUnit 4、Truth。

## 全局约束

- 仅在已经位于首页时响应重复点击。
- 有内容时必须同时展开 App Bar、滚动列表到顶部并刷新。
- 空列表时只刷新。
- 不新增全局状态或数据层接口。

### 任务一：首页重复点击动作决策

**文件：**

- 测试：`app/src/test/java/app/mystery0/nodeflow/feature/home/HomeScreenContentTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/home/HomeScreen.kt`

- [x] 新增失败断言，要求有内容时 `HomeReselectAction.shouldExpandAppBar` 为 `true`，空列表时为 `false`。
- [x] 运行 `HomeScreenContentTest`，确认测试因字段不存在而失败。
- [x] 在 `HomeReselectAction` 和 `homeReselectAction` 中实现最小决策逻辑。
- [x] 重跑局部测试并确认通过。

### 任务二：展开 App Bar 并完成验证

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/home/HomeScreen.kt`
- 修改：`docs/subsystems/ui-navigation.md`

- [x] 保存 `TopAppBarState`，收到有内容的重复点击事件时将 `heightOffset` 设置为 `0f`，并保留现有列表滚动与刷新逻辑。
- [x] 更新 UI 与导航专题文档中的首页重复点击行为。
- [x] 运行首页局部测试、`:app:testDebugUnitTest` 和 `:app:assembleDebug`。
- [x] 检查最终差异并记录仍需模拟器或真机验证的交互项。
