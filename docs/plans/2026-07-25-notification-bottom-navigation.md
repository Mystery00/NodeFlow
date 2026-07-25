# 消息底部导航实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: 使用 `executing-plans` 在当前会话逐项执行；所有步骤使用复选框跟踪，不创建提交。

**目标：** 将通知中心迁移为主页面一级目的地，在底栏新增始终可见的“消息”按钮，并提前展示未读消息 Badge。

**架构：** `MainShell` 继续持有唯一的 `AccountViewModel`，使用 `AccountUiState` 同时驱动签到 Badge、消息 Badge 和登录状态。`NotificationViewModel` 只在已登录用户进入内部 `notification` 目的地时创建，通知中的详情跳转继续交给根 NavHost。

**技术栈：** Kotlin、Coroutines/StateFlow、Jetpack Compose、Material 3、Navigation Compose、Paging 3、Koin、JUnit、Truth。

## 全局约束

- 底栏顺序固定为“首页、节点、消息、我的”，未登录时也显示“消息”。
- 复用现有账号概览、通知 Repository、PagingSource、网络、Cookie、Parser、富文本和链接路由能力。
- 未登录进入消息页时不得创建通知分页请求；登录后返回应自动加载。
- 点击“消息”后沿用 `AccountUiEvent.NotificationsOpened` 在本地清除已展示未读数。
- 不访问真实写接口，不记录通知正文、用户名、Cookie 或完整响应 HTML。
- 代码注释和文档使用中文，日志使用英文。
- 保留工作区中与本需求无关的 `gradle/libs.versions.toml` 修改，不提交、不推送。

---

### 任务一：定义一级路由、Badge 和页面决策

**文件：**

- 修改：`app/src/test/java/app/mystery0/nodeflow/navigation/NavigationRouteTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowDestinations.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/MainShell.kt`

**接口：**

- `fun messageBottomBarRoute(): String`
- `fun isMessageBottomBarSelected(currentRoute: String?): Boolean`
- `fun messageBottomBarBadgeText(state: AccountUiState): String?`
- `fun shouldLoadNotificationList(state: AccountUiState): Boolean`

- [x] **步骤一：添加失败测试。** 在 `NavigationRouteTest` 覆盖 `Notification` 属于一级路由、消息底栏目标和选中状态，并使用已登录/未登录的 `AccountUiState` 覆盖加载中、0、8、100、获取失败及登录状态的 Badge 文案和分页页面决策。
- [x] **步骤二：验证 RED。** 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "*NavigationRouteTest" --console=plain`，预期因新函数缺失且 `Notification` 尚未属于一级路由而失败。
- [x] **步骤三：实现最小纯函数。** `messageBottomBarRoute()` 返回 `NodeFlowDestinations.Notification`；选中判断只匹配该路由；Badge 在未登录时返回 `null`，其余规则为加载中 `null`、未知 `!`、0 `null`、超过 99 为 `99+`；分页页面决策返回 `state.isLoggedIn`。
- [x] **步骤四：更新一级路由集合。** 将 `NodeFlowDestinations.Notification` 加入 `isTopLevelRoute()` 的集合。
- [x] **步骤五：验证 GREEN。** 重新运行同一局部测试，预期全部通过。

### 任务二：迁移通知入口与页面层级

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/MainShell.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowNavHost.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/account/AccountScreen.kt`
- 修改：`app/src/test/java/app/mystery0/nodeflow/feature/account/AccountScreenContentTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/notification/NotificationScreen.kt`

**接口：**

- `MainShell` 新增 `onUserClick: (String) -> Unit` 和 `onNotificationTopicClick: (Long, Int?) -> Unit`，移除 `onNotificationClick`。
- `NotificationScreen` 移除 `onBackClick`，标题改为“消息”。
- 新增 `NotificationSignedOutScreen(onLoginClick: () -> Unit, modifier: Modifier = Modifier)`。

- [x] **步骤一：扩展底栏。** 在 `NodeFlowBottomBar` 增加 `messageBadgeText` 和 `onMessageClick`，使用 `Icons.Outlined.Notifications`、`BadgedBox` 和“消息”标签，放在“节点”和“我的”之间。
- [x] **步骤二：接入点击行为。** 点击消息时先发送 `AccountUiEvent.NotificationsOpened`，再通过内部 NavController 导航到 `messageBottomBarRoute()`。
- [x] **步骤三：迁移通知目的地。** 在 `MainShell` 内添加 `notification` composable；未登录时渲染 `NotificationSignedOutScreen`，已登录时才创建 `NotificationViewModel`、收集 `LazyPagingItems` 并渲染 `NotificationScreen`。
- [x] **步骤四：调整根导航。** `NodeFlowNavHost` 为 `MainShell` 提供用户与主题详情回调，删除根级通知 composable 以及对应 imports 和 `onNotificationClick`。
- [x] **步骤五：调整消息页外观。** `NotificationScreen` 删除返回图标及回调，标题改为“消息”；新增带 Top App Bar、说明文字和“登录”按钮的未登录页面。
- [x] **步骤六：移除个人页入口。** 从 `AccountScreen` 删除铃铛按钮、Badge imports、`onNotificationClick` 参数和 `notificationBadgeText`；从 `AccountScreenContentTest` 删除已迁移到导航测试的旧 Badge 测试。
- [x] **步骤七：编译与局部验证。** 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "*NavigationRouteTest" --tests "*AccountScreenContentTest" --console=plain`，预期通过。

### 任务三：同步现行文档并完成自动化验证

**文件：**

- 修改：`docs/subsystems/ui-navigation.md`
- 修改：`docs/index.md`

- [x] **步骤一：更新现行导航说明。** 将个人页铃铛和根级通知中心的旧规则改为四项底栏、消息 Badge、未登录引导及内部通知目的地规则，并链接本次设计。
- [x] **步骤二：补全索引。** 将本次设计条目改为设计文档与实施计划的成对链接。
- [x] **步骤三：运行全量验证。** 依次运行 `.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --console=plain` 和 `.\gradlew.bat :app:lintDebug --console=plain`，预期均为 `BUILD SUCCESSFUL`。

### 任务四：模拟器与交付检查

**文件：**

- 不新增代码文件。

- [x] **步骤一：安装 APK。** 将 `app/build/outputs/apk/debug/app-debug.apk` 使用 `adb install -r` 安装到已连接模拟器，保留现有登录数据。
- [x] **步骤二：验证底栏与 Badge。** 已确认四项底栏顺序和切换布局；模拟器账号当前未读数为 0，数字、`99+`、未知及未登录 Badge 分支由单元测试验证，不伪造服务器数据。
- [x] **步骤三：验证消息页与返回栈。** 检查消息页无返回箭头、刷新按钮可见、列表正常加载；只打开一条消息的详情并返回，确认回到消息页，不执行回复或其他写操作。
- [x] **步骤四：验证未登录决策。** 使用自动化测试证明未登录不创建通知列表；模拟器当前已登录，不退出真实账号。
- [x] **步骤五：完成自检。** 运行 `git diff --check`，检查改动范围、日志和敏感信息，确认未覆盖用户无关修改。
