# 首页重复点击展开 App Bar 设计

## 目标

用户已经位于首页并再次点击首页底部导航项时，主题列表回到顶部、顶部 App Bar 完整展开，并触发一次刷新。用户应能同时看到页面标题和刷新状态，明确感知列表已经回到首页顶部。

## 方案

保留现有 `MainShell` 首页重复点击事件和 `HomeScreen` 列表状态，不修改导航层或数据层。`HomeScreen` 收到事件后立即发送 `HomeUiEvent.Refresh`；列表有内容时，同时执行以下界面动作：

- 调用 `LazyListState.animateScrollToItem(0)` 将主题列表滚动到首项。
- 将 `TopAppBarState.heightOffset` 恢复为 `0f`，完整展开 App Bar。

App Bar 状态与列表状态属于同一个页面，由 `HomeScreen` 统一处理。空列表仍只刷新，不执行无意义的滚动或 App Bar 状态调整。

## 测试与验证

- 扩展纯 Kotlin 决策测试，验证有内容时同时请求滚动、展开 App Bar 和刷新，空列表时只刷新。
- 运行首页局部测试、全量 JVM 单元测试和 Debug 构建。
- 模拟器或真机检查 App Bar 已折叠时重复点击首页，确认标题栏展开、列表回到首项且刷新指示器出现。
