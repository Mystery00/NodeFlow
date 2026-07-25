# “我的”签到提醒 Badge 实施计划

> **执行要求：** 按测试驱动方式逐项实现；完成后按用户后续要求创建提交。

**目标：** 登录用户当天尚未签到时，在底部导航栏“我的”图标上显示 `!` Badge，并在签到成功后自动消失。

**架构：** 将现有 `AccountViewModel` 提升到 `MainShell` 作用域，主页面启动后即加载账号概览。同一个 `AccountUiState` 同时驱动底栏 Badge 和 `AccountScreen`，避免重复请求和状态同步。

**技术栈：** Kotlin、Coroutines/StateFlow、Jetpack Compose、Material 3、Koin、JUnit、Truth。

## 全局约束

- 仅在已登录且 `AccountUiState.overview.checkIn.canCheckIn == true` 时显示 `!` Badge。
- 未登录、加载中、加载失败、签到状态未知和已经签到时不显示 Badge。
- 复用现有账号概览请求与签到流程，不新增网络请求类型、Repository 或 ViewModel。
- 代码注释和文档使用中文，日志使用英文。
- 保留工作区中与本需求无关的 `gradle/libs.versions.toml` 修改，不提交、不推送。

---

### 任务一：用纯状态判断定义 Badge 行为

**文件：**

- 修改：`app/src/test/java/app/mystery0/nodeflow/navigation/NavigationRouteTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/MainShell.kt`

**接口：**

- 输入：`AccountUiState`
- 输出：`fun shouldShowAccountCheckInBadge(state: AccountUiState): Boolean`

- [x] 在 `NavigationRouteTest` 添加测试，分别构造待签到、已签到、未登录和未知签到状态，断言只有已登录且可签到时返回 `true`。
- [x] 运行 `.\gradlew.bat :app:testDebugUnitTest --tests "*NavigationRouteTest"`，确认测试因 `shouldShowAccountCheckInBadge` 尚不存在而编译失败。
- [x] 在 `MainShell.kt` 添加最小纯函数：返回 `state.isLoggedIn && state.overview?.checkIn?.canCheckIn == true`。
- [x] 重新运行同一局部测试，确认通过。

### 任务二：让底栏与“我的”页面共享账号状态

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/navigation/MainShell.kt`
- 修改：`docs/index.md`

**接口：**

- `NodeFlowBottomBar` 新增 `showAccountCheckInBadge: Boolean` 参数。
- `MainShell` 创建并收集唯一的 `AccountViewModel`，向底栏传递 Badge 状态，向 `AccountScreen` 传递同一份 `AccountUiState`。

- [x] 在 `MainShell` 顶层通过 `koinViewModel<AccountViewModel>()` 获取 ViewModel，并使用 `collectAsStateWithLifecycle()` 收集状态。
- [x] 移除账号目的地内重复创建和收集 ViewModel 的代码，继续使用同一 ViewModel 处理 `AccountUiEvent`。
- [x] 使用 Material 3 `BadgedBox` 包裹“我的”图标，仅在 `showAccountCheckInBadge` 为 `true` 时渲染 `Badge { Text("!") }`。
- [x] 在 `docs/index.md` 将本设计条目补充为设计文档与实施计划的成对链接。
- [x] 运行局部导航测试、全部 `:app:testDebugUnitTest` 和 `:app:assembleDebug`。

### 任务三：模拟器与交付检查

**文件：**

- 不新增代码文件。

- [x] 检查已连接模拟器和当前登录状态，安装最新 Debug APK。
- [x] 启动应用并确认底栏“我的”图标上的 `!` Badge 与“我的”页签到按钮状态一致。
- [x] 检查首页与“我的”页面切换、Badge 位置及应用稳定性；不执行真实签到。
- [x] 检查 `git diff --check`、改动范围和敏感信息，确认未覆盖用户的无关修改。
