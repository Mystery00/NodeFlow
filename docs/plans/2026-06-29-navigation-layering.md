# 导航层级调整 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让首页、节点列表、设置作为一级底部导航页面，帖子详情和节点详情作为无底栏的二级页面。

**Architecture:** 保留单 `NavHost`，新增 `nodes` 一级路由，并通过纯函数判断当前 route 是否显示底部导航栏。帖子详情和节点详情继续使用现有详情页面，但节点详情新增返回入口；节点列表先使用 Compose 占位页并提供常用节点 chip 验证跳转。

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Navigation Compose, Koin, JUnit4, Truth。

## Global Constraints

- 代码注释和文档使用中文。
- 日志打印使用英文。
- 不引入新依赖。
- 不改变设置页业务行为。
- 不重新引入根导航重复顶部 padding 问题。

---

### Task 1: 导航路由和底栏显示规则

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowDestinations.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowNavHost.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/navigation/NavigationRouteTest.kt`

**Interfaces:**
- Produces: `NodeFlowDestinations.NodeList: String`
- Produces: `fun NodeFlowDestinations.isTopLevelRoute(route: String?): Boolean`
- Consumes: existing `rootNavHostPadding(scaffoldPadding: PaddingValues): PaddingValues`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/mystery0/nodeflow/navigation/NavigationRouteTest.kt`:

```kotlin
package app.mystery0.nodeflow.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigationRouteTest {
    @Test
    fun isTopLevelRoute_returnsTrueForBottomNavigationRoutes() {
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Home)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeList)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Settings)).isTrue()
    }

    @Test
    fun isTopLevelRoute_returnsFalseForDetailRoutes() {
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.TopicRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.ProfileRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(null)).isFalse()
    }

    @Test
    fun nodeList_usesStableRouteName() {
        assertThat(NodeFlowDestinations.NodeList).isEqualTo("nodes")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.navigation.NavigationRouteTest"
```

Expected: FAIL because `NodeList` and `isTopLevelRoute` do not exist.

- [ ] **Step 3: Write minimal implementation**

Update `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowDestinations.kt`:

```kotlin
object NodeFlowDestinations {
    const val Home = "home"
    const val NodeList = "nodes"
    const val Settings = "settings"
    const val NodeRoute = "node/{nodeName}"
    const val TopicRoute = "topic/{topicId}"
    const val ProfileRoute = "profile/{username}"
    const val Auth = "auth"
    const val Notification = "notification"
    const val Editor = "editor"

    fun node(nodeName: String = "python"): String = "node/${Uri.encode(nodeName)}"
    fun topic(topicId: Long): String = "topic/$topicId"
    fun profile(username: String): String = "profile/${Uri.encode(username)}"

    fun isTopLevelRoute(route: String?): Boolean = route in setOf(Home, NodeList, Settings)
}
```

Update `NodeFlowNavHost.kt` later in Task 3 to consume this function.

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.navigation.NavigationRouteTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app\src\main\java\app\mystery0\nodeflow\navigation\NodeFlowDestinations.kt app\src\test\java\app\mystery0\nodeflow\navigation\NavigationRouteTest.kt
git commit -m "导航：新增一级节点列表路由"
```

### Task 2: 节点列表占位页

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/feature/node/NodeListScreen.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/feature/node/NodeListContentTest.kt`

**Interfaces:**
- Produces: `data class NodeListChip(val name: String, val title: String)`
- Produces: `fun defaultNodeListChips(): List<NodeListChip>`
- Produces: `@Composable fun NodeListScreen(onNodeClick: (String) -> Unit, modifier: Modifier = Modifier)`
- Consumes: `NodeChip(title: String, onClick: () -> Unit)`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/mystery0/nodeflow/feature/node/NodeListContentTest.kt`:

```kotlin
package app.mystery0.nodeflow.feature.node

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodeListContentTest {
    @Test
    fun defaultNodeListChips_containsStableStarterNodes() {
        val chips = defaultNodeListChips()

        assertThat(chips.map { it.name }).containsAtLeast("python", "android", "programmer", "create")
    }

    @Test
    fun defaultNodeListChips_hasReadableTitles() {
        val chips = defaultNodeListChips()

        assertThat(chips.first { it.name == "android" }.title).isEqualTo("Android")
        assertThat(chips.first { it.name == "python" }.title).isEqualTo("Python")
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.node.NodeListContentTest"
```

Expected: FAIL because `defaultNodeListChips` does not exist.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/app/mystery0/nodeflow/feature/node/NodeListScreen.kt`:

```kotlin
package app.mystery0.nodeflow.feature.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.NodeChip

data class NodeListChip(
    val name: String,
    val title: String,
)

fun defaultNodeListChips(): List<NodeListChip> = listOf(
    NodeListChip(name = "python", title = "Python"),
    NodeListChip(name = "android", title = "Android"),
    NodeListChip(name = "programmer", title = "程序员"),
    NodeListChip(name = "create", title = "分享创造"),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NodeListScreen(
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("节点") })
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "节点列表正在建设中",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "先从常用节点进入详情页，后续会补充分组、搜索和收藏。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                defaultNodeListChips().forEach { chip ->
                    NodeChip(
                        title = chip.title,
                        onClick = { onNodeClick(chip.name) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.node.NodeListContentTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app\src\main\java\app\mystery0\nodeflow\feature\node\NodeListScreen.kt app\src\test\java\app\mystery0\nodeflow\feature\node\NodeListContentTest.kt
git commit -m "页面：添加节点列表占位页"
```

### Task 3: 根导航接入节点列表并隐藏详情页底栏

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowNavHost.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/node/NodeScreen.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/navigation/RootScaffoldPaddingTest.kt`

**Interfaces:**
- Consumes: `NodeFlowDestinations.NodeList`
- Consumes: `NodeFlowDestinations.isTopLevelRoute(route: String?): Boolean`
- Consumes: `NodeListScreen(onNodeClick: (String) -> Unit, modifier: Modifier = Modifier)`
- Produces: `NodeScreen(..., onBackClick: () -> Unit, ...)`

- [ ] **Step 1: Write the failing test**

Update `RootScaffoldPaddingTest.kt` with bottom bar visibility expectations:

```kotlin
@Test
fun isTopLevelRoute_keepsBottomBarOnlyForRootTabs() {
    assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Home)).isTrue()
    assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeList)).isTrue()
    assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Settings)).isTrue()
    assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.TopicRoute)).isFalse()
    assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeRoute)).isFalse()
}
```

If Task 1 already added equivalent coverage, keep this test out and rely on `NavigationRouteTest`.

- [ ] **Step 2: Run relevant tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.navigation.*"
```

Expected: PASS after Task 1.

- [ ] **Step 3: Update `NodeScreen` signature and top bar**

Modify `NodeScreen`:

```kotlin
fun NodeScreen(
    state: NodeUiState,
    onEvent: (NodeUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
)
```

Add imports:

```kotlin
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
```

Update `TopAppBar`:

```kotlin
TopAppBar(
    title = { Text(state.node?.title ?: state.nodeName) },
    navigationIcon = {
        IconButton(onClick = onBackClick) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
        }
    },
    actions = {
        IconButton(onClick = { onEvent(NodeUiEvent.Refresh) }) {
            Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
        }
    },
)
```

- [ ] **Step 4: Update `NodeFlowNavHost` bottom bar visibility**

In `NodeFlowNavHost`, compute route once:

```kotlin
val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
```

Update `Scaffold(bottomBar = ...)`:

```kotlin
bottomBar = {
    if (NodeFlowDestinations.isTopLevelRoute(currentRoute)) {
        NodeFlowBottomBar(
            currentRoute = currentRoute,
            onHomeClick = {
                navController.navigateTopLevel(NodeFlowDestinations.Home)
            },
            onNodeClick = {
                navController.navigateTopLevel(NodeFlowDestinations.NodeList)
            },
            onSettingsClick = {
                navController.navigateTopLevel(NodeFlowDestinations.Settings)
            },
        )
    }
}
```

Add the node list composable:

```kotlin
composable(NodeFlowDestinations.NodeList) {
    NodeListScreen(
        onNodeClick = { nodeName ->
            navController.navigate(NodeFlowDestinations.node(nodeName))
        },
    )
}
```

Update node detail composable:

```kotlin
NodeScreen(
    state = state,
    onEvent = viewModel::onEvent,
    onBackClick = { navController.popBackStack() },
    onTopicClick = { topic ->
        navController.navigate(NodeFlowDestinations.topic(topic.id))
    },
    onNodeClick = { nodeName ->
        navController.navigate(NodeFlowDestinations.node(nodeName))
    },
)
```

Update bottom bar selected state:

```kotlin
selected = currentRoute == NodeFlowDestinations.NodeList
```

- [ ] **Step 5: Run tests and build**

Run sequentially:

```powershell
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Expected: all PASS.

- [ ] **Step 6: Commit**

```powershell
git add app\src\main\java\app\mystery0\nodeflow\navigation\NodeFlowNavHost.kt app\src\main\java\app\mystery0\nodeflow\feature\node\NodeScreen.kt app\src\test\java\app\mystery0\nodeflow\navigation\RootScaffoldPaddingTest.kt
git commit -m "导航：隐藏详情页底部导航栏"
```

### Task 4: 模拟器验证与最终整理

**Files:**
- Modify only if verification reveals a bug in files from Task 1-3.

**Interfaces:**
- Consumes: built APK `app/build/outputs/apk/debug/app-debug.apk`

- [ ] **Step 1: Install and launch debug APK**

Run:

```powershell
adb devices
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -S -n app.mystery0.nodeflow/.MainActivity
```

Expected: emulator is connected and app launches.

- [ ] **Step 2: Verify bottom bar visibility manually**

Use screenshots or UIAutomator dump to confirm:

```powershell
adb shell screencap -p /sdcard/nodeflow-home.png
adb pull /sdcard/nodeflow-home.png build\nodeflow-home.png
```

Expected:

- 首页显示底部导航栏。
- 点击帖子进入帖子详情后，底部导航栏不显示。
- 帖子详情左上角返回回到首页。
- 点击底部“节点”进入节点列表占位页，底部导航栏显示。
- 点击节点 chip 进入节点详情后，底部导航栏不显示。
- 节点详情左上角返回回到节点列表。
- 设置页保持底部导航栏和现有内容。

- [ ] **Step 3: Final status check**

Run:

```powershell
git status --short
git log -3 --oneline
```

Expected: worktree clean after commits, latest commits are the navigation changes.
