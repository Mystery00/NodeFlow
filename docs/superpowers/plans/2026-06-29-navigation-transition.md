# 导航转场体验调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将底部导航栏移入主页面 Shell，让帖子详情和节点详情作为根级全屏页面覆盖进入，避免底栏在数据加载前后突然消失或出现。

**Architecture:** 根 `NavHost` 只负责 `main` 和详情页路由；`MainShell` 内部负责首页、节点列表、设置和底部导航栏。根级详情页使用 Navigation Compose 的 slide/fade 转场，主页面 Shell 稳定保留在下层。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Koin, JUnit4, Truth。

## Global Constraints

- 代码注释和文档使用中文。
- 日志打印使用英文。
- 不引入新依赖。
- 不改变首页、节点列表、设置、帖子详情、节点详情的数据加载逻辑。
- 不重新引入根导航重复顶部 padding 问题。

---

### Task 1: 根路由语义

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowDestinations.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/navigation/NavigationRouteTest.kt`

**Interfaces:**
- Produces: `NodeFlowDestinations.Main: String`
- Produces: `fun rootStartDestination(): String`
- Produces: `fun NodeFlowDestinations.isRootDetailRoute(route: String?): Boolean`

- [ ] **Step 1: Write failing tests**

Add tests asserting:

```kotlin
assertThat(rootStartDestination()).isEqualTo(NodeFlowDestinations.Main)
assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.TopicRoute)).isTrue()
assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.NodeRoute)).isTrue()
assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Home)).isFalse()
assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.NodeList)).isFalse()
assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Settings)).isFalse()
```

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.navigation.NavigationRouteTest"
```

Expected: FAIL because `Main`, `rootStartDestination`, and `isRootDetailRoute` do not exist.

- [ ] **Step 2: Implement root route helpers**

Add:

```kotlin
const val Main = "main"
fun isRootDetailRoute(route: String?): Boolean = route in setOf(NodeRoute, TopicRoute, ProfileRoute)
fun rootStartDestination(): String = NodeFlowDestinations.Main
```

- [ ] **Step 3: Verify and commit**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.navigation.NavigationRouteTest"
```

Commit:

```powershell
git add app\src\main\java\app\mystery0\nodeflow\navigation\NodeFlowDestinations.kt app\src\test\java\app\mystery0\nodeflow\navigation\NavigationRouteTest.kt
git commit -m "导航：新增根级页面路由语义"
```

### Task 2: 拆出 MainShell

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/navigation/MainShell.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowNavHost.kt`

**Interfaces:**
- Produces: `@Composable fun MainShell(onTopicClick: (Topic) -> Unit, onNodeClick: (String) -> Unit, modifier: Modifier = Modifier)`
- Consumes: existing `HomeScreen`, `NodeListScreen`, `SettingsScreen`

- [ ] **Step 1: Move bottom navigation into MainShell**

Create `MainShell.kt` with:

- `rememberNavController()` for main tab navigation.
- `Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = { NodeFlowBottomBar(...) })`
- nested `NavHost(startDestination = NodeFlowDestinations.Home)`
- routes: `home`, `nodes`, `settings`
- callbacks to root: `onTopicClick(topic)`, `onNodeClick(nodeName)`

- [ ] **Step 2: Keep reusable helpers in navigation package**

Move or keep these helpers accessible from tests:

```kotlin
fun nodeBottomBarRoute(): String = NodeFlowDestinations.NodeList
fun isNodeBottomBarSelected(currentRoute: String?): Boolean = currentRoute == NodeFlowDestinations.NodeList
fun rootNavHostPadding(scaffoldPadding: PaddingValues): PaddingValues = PaddingValues(
    bottom = scaffoldPadding.calculateBottomPadding(),
)
```

- [ ] **Step 3: Verify nested shell compiles**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.navigation.*"
```

Expected: PASS.

### Task 3: 根 NavHost 只承载 MainShell 和详情页

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowNavHost.kt`

**Interfaces:**
- Consumes: `MainShell`
- Consumes: `rootStartDestination()`
- Consumes: `NodeFlowDestinations.isRootDetailRoute(route)`

- [ ] **Step 1: Update root NavHost**

Root `NavHost` should:

- `startDestination = rootStartDestination()`
- `composable(NodeFlowDestinations.Main) { MainShell(...) }`
- keep root `topic/{topicId}`, `node/{nodeName}`, `profile/{username}` and existing full-screen placeholders.
- remove root `Scaffold` bottom bar.

- [ ] **Step 2: Add root detail transitions**

Use Navigation Compose transitions:

- detail enter: slide into container from right + fade in.
- detail pop exit: slide out to right + fade out.
- main route enter/exit: no special animation.

- [ ] **Step 3: Verify and commit**

Run sequentially:

```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Commit:

```powershell
git add app\src\main\java\app\mystery0\nodeflow\navigation\NodeFlowNavHost.kt app\src\main\java\app\mystery0\nodeflow\navigation\MainShell.kt
git commit -m "导航：使用主页面 Shell 优化详情转场"
```

### Task 4: 模拟器验证

**Files:**
- Modify only if verification reveals a concrete bug.

**Verification:**

Run:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -S -n app.mystery0.nodeflow/.MainActivity
```

Confirm:

- 首页、节点列表、设置页显示底部导航栏。
- 点击帖子进入详情时，详情页覆盖进入，底栏不再先行消失。
- 返回帖子详情时，主页面和底栏自然恢复。
- 点击节点进入节点详情时，详情页覆盖进入，底栏不再先行消失。
- 返回节点详情时，节点列表和底栏自然恢复。

Final check:

```powershell
git status --short
git log -5 --oneline
```
