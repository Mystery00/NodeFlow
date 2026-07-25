# 消息底部导航设计

## 目标

将现有通知中心从“我的”页面右上角入口迁移为主页面一级目的地，与“首页”“节点”“我的”平级。底部导航栏新增“消息”按钮，并在用户进入“我的”页面之前展示已加载的未读消息 Badge。

## 导航层级

主页面底部导航顺序调整为：

```text
首页 → 节点 → 消息 → 我的
```

- `NodeFlowDestinations.Notification` 加入一级路由集合。
- 通知目的地从根 `NavHost` 移入 `MainShell` 的内部 `NavHost`。
- “我的”页面移除铃铛入口，保留刷新和设置操作。
- 消息页作为一级页面不显示返回箭头，标题由“通知”改为“消息”。
- 点击消息中的用户、主题或目标回复仍通过根导航打开详情页；关闭详情页后回到消息页。

## 未读状态与 Badge

继续复用 `AccountViewModel` 加载的 `AccountUiState.overview.unreadNotificationCount`，不新增未读请求或共享状态实现。由于 `AccountViewModel` 已由 `MainShell` 持有，登录用户进入主页面后即可在后台加载未读数。

Badge 规则保持现有语义：

- 未读数为 1–99 时显示实际数字。
- 未读数超过 99 时显示 `99+`。
- 已登录、加载完成但无法取得未读数时显示 `!`。
- 加载中、未读数为 0 或未登录时不显示。
- 点击“消息”底栏时，将当前展示的未读数在本地清零，再进入消息页。

消息列表加载失败时只展示原有错误状态，不恢复或伪造未读数。

## 登录状态

“消息”底栏始终显示，避免登录状态变化导致底栏结构和点击位置改变。

- 未登录进入消息页时，不创建 `NotificationViewModel`，也不发起通知分页请求。
- 页面展示登录引导，用户可进入现有登录流程。
- 登录成功返回消息页后，`AccountUiState` 自动更新，随后创建通知分页状态并加载消息。

## 组件与数据流

```text
AccountViewModel
    └─ AccountUiState.unreadNotificationCount
       └─ MainShell → 消息底栏 Badge

MainShell 内部 NavHost
    └─ notification
       ├─ 未登录 → 消息登录引导
       └─ 已登录 → NotificationViewModel → NotificationScreen

NotificationScreen
    └─ 用户/主题点击 → 根 NavHost 详情页
```

通知 Repository、PagingSource、远端数据源、HTML Parser 和消息卡片不做业务改造。

## 测试与验证

- 导航测试覆盖通知路由属于一级页面、消息底栏目标与选中状态。
- Badge 纯函数测试覆盖未登录、加载中、0、普通未读数、超过 99 和获取失败。
- 页面决策测试覆盖未登录时不创建分页页面、登录后进入消息列表。
- 更新个人页测试，确认铃铛入口相关展示逻辑已经迁移。
- 运行相关局部测试、全部 `:app:testDebugUnitTest`、`:app:assembleDebug` 和 `:app:lintDebug`。
- 在模拟器验证四项底栏、未登录引导、登录用户未读 Badge、消息页刷新、详情页返回栈和深浅色布局；不执行删除、回复或其他写操作。
