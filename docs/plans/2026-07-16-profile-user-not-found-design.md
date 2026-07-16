# 用户主页 404 友好占位设计

- 日期：2026-07-16
- 状态：已确认
- 相关子系统：网络（`core/network`）、用户资料（`data/user`、`feature/profile`）、设计系统（`core/designsystem`）

## 背景

在帖子详情页点击发帖人头像/用户名可进入用户主页。若该用户已被 V2EX 停用（ban）或不存在，
`/api/members/show.json` 与 `/member/{username}` 页面均返回 HTTP 404，
`bodyStringOrThrow()` 抛出 `NodeFlowException(Kind.Http, "请求失败：HTTP 404")`，
用户主页因此展示通用错误态「请求失败：HTTP 404」和一个永远不会成功的「重试」按钮，体验糟糕。

## 目标

- 用户不存在或账号已被停用时，用户主页展示语义清晰的占位状态，不再出现裸的 HTTP 状态码。
- 不再对此类不可恢复错误提供无意义的「重试」按钮。
- 其他错误（网络失败、解析失败等）维持现有「错误 + 重试」行为不变。

## 方案

采用「友好占位页」方案（已与用户确认），不额外抓取 HTML 区分「被停用」与「不存在」两种子状态，
统一文案覆盖两种情况。

### 错误类型（core 层）

- `NodeFlowException.Kind` 新增 `NotFound`。
- `NetworkCalls.kt` 中 `bodyStringOrThrow()` / `bodyBytesOrThrow()` 在响应码为 404 时
  抛出 `Kind.NotFound`（非 404 仍为 `Kind.Http`），消息文案保持「请求失败：HTTP 404」，
  其他页面展示行为不受影响。
- `NodeFlowException.kt` 新增 `Throwable.isNotFound()` 扩展，与现有 `isAccessDenied()` 风格一致。

### Profile 页（feature 层）

- `ProfileUiState` 新增 `userNotFound: Boolean = false`。
- `ProfileViewModel` 加载失败时若 `error.isNotFound()` 则置 `userNotFound = true`，
  成功或其他错误时保持 / 复位为 `false`。
- `ProfileScreen` 渲染分支：`userNotFound && user == null` 时展示占位状态
  （`PersonOff` 图标 + 「该用户不存在，或账号已被停用」，无重试按钮）；
  其余错误维持 `ErrorContent`。

### 设计系统

- `EmptyContent` 增加可选 `icon: ImageVector = Icons.Outlined.Inbox` 参数，
  Profile 页复用该组件传入 `PersonOff` 图标，不新造组件。

## 边界情况

- 曾经查看过该用户（本地有缓存）时，`UserRepositoryImpl` 失败回退缓存的行为保持不变：
  刷新失败会继续展示旧资料。此时 `user != null`，占位页不生效，维持现状。
- 顶部栏刷新按钮保留：占位状态下用户仍可手动刷新（等价于重试，但不再作为主操作误导）。

## 测试

- `NetworkCallsTest`（或就近测试）：404 响应经 `bodyStringOrThrow()` 抛出 `Kind.NotFound`；
  其他非 2xx 仍为 `Kind.Http`。
- `ProfileViewModelTest`（新增）：加载返回 `NotFound` 失败 → `userNotFound = true` 且无用户数据；
  其他错误 → `userNotFound = false` 且 `errorMessage` 生效。
- 验证命令：`.\gradlew.bat :app:testDebugUnitTest` 与 `.\gradlew.bat :app:assembleDebug`。
