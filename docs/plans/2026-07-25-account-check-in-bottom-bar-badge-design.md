# “我的”签到提醒 Badge 设计

## 目标

已登录用户启动应用后，在后台加载现有账号概览。如果当天尚未签到，则在底部导航栏“我的”图标上显示内容为 `!` 的 Badge；签到成功后 Badge 自动消失。

## 状态与显示规则

- 仅当用户已登录且 `AccountUiState.overview.checkIn.canCheckIn == true` 时显示 Badge。
- Badge 使用 Material 3 `BadgedBox` 和 `Badge`，内容固定为 `!`。
- 未登录、账号概览加载中、加载失败、签到状态未知或当天已经签到时不显示 Badge。
- Badge 只用于提醒，不改变“我的”底部导航项的点击和选中行为。

## 架构与数据流

`MainShell` 在主页面生命周期内创建并收集一个 `AccountViewModel`。该 ViewModel 沿用现有登录会话观察和账号概览加载逻辑，因此应用进入主页面后即可在后台取得签到状态。

同一份 `AccountUiState` 同时提供给底部导航栏和 `AccountScreen`：

```text
AccountViewModel
    └─ AccountUiState
       ├─ NodeFlowBottomBar → “我的”签到提醒 Badge
       └─ AccountScreen → 账号资料与签到按钮
```

不新增独立的底栏 ViewModel，不增加重复的账号概览请求，也不把状态下沉为 Repository 级共享流。

## 状态更新

- 登录会话有效时，现有 `AccountViewModel` 自动加载账号概览，底栏根据加载结果更新。
- 用户在“我的”页面签到成功后，现有逻辑刷新账号概览；同一个 StateFlow 更新后，签到按钮与底栏 Badge 同时消失。
- 用户退出登录后，现有逻辑清空账号概览，Badge 随之消失。
- 加载或刷新失败时不使用 `!` 表示错误，避免把未知状态误报为未签到。

## 测试与验证

- 为底栏 Badge 的纯状态判断先添加失败单元测试，覆盖待签到、已签到、未登录和状态未知。
- 运行相关导航单元测试和全部 `:app:testDebugUnitTest`。
- 运行 `:app:assembleDebug`，确认 Compose 与导航改动可以构建。
- 在模拟器或真机检查 Badge 的位置、深浅色显示、页面切换及签到成功后的消失行为；验证时不执行未经用户授权的真实签到。
