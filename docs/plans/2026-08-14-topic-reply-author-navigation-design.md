# 帖子回复作者导航设计

## 背景

帖子详情头部的发帖人用户名已经可以进入用户详情页，回复正文中的 V2EX 用户链接也会复用站内链接路由；但回复卡片的作者头像和用户名只是展示内容，没有把 `TopicDetailScreen` 已有的 `onUserClick` 导航能力传给 `ReplyItem`。

## 目标

- 点击任意回复作者的头像进入该作者的用户详情页。
- 点击任意回复作者的用户名进入同一用户详情页。
- 作者名为空时不产生无效导航。
- 保留回复、更多操作、引用预览、正文链接和图片预览的现有交互。

## 方案

在 `core.ui.ReplyItem` 增加 `onUserClick: (String) -> Unit` 参数。新增纯 Kotlin 函数 `replyAuthorNavigationTarget(Reply)`，统一对作者名执行首尾空白清理并在结果为空时返回 `null`。头像和用户名分别获得独立点击语义，但都只使用该函数返回的同一导航目标。

`TopicDetailScreen` 在渲染每条回复时把现有 `onUserClick` 继续传入 `ReplyItem`，导航图和用户详情路由不变。正文中的 `/member/{username}` 链接继续由 `V2exLinkParser` 处理，不建立第二套路由规则。

## 测试与验证

- JVM 单元测试覆盖有效用户名去空格以及空用户名不导航。
- 运行 `ReplyItemAuthorTest`、全部 JVM 单元测试和 Debug 构建。
- 在模拟器进入包含多位回复者的帖子，分别点击回复头像和用户名，确认进入对应用户详情并能返回原帖。

