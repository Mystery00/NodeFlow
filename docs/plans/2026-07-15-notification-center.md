# 通知中心实施计划

> **执行要求：** 当前任务内按测试驱动方式逐项实现，不在用户要求前创建提交。

**目标：** 在个人页提供带未读 Badge 的通知入口，并实现可分页、可跳转、支持引用预览的通知中心。

**架构：** `AccountScreen → root navigation → NotificationScreen → NotificationViewModel → NotificationRepository → NotificationPagingSource → NotificationRemoteDataSource → V2exRawApi`。引用内容由 Repository 复用 TopicRepository 按需补全，无法可靠定位时安全降级。

**技术栈：** Kotlin、Jetpack Compose、Material 3、Paging 3、Retrofit/OkHttp、Jsoup、Koin、JUnit/Truth。

## 全局约束

- 复用现有 Cookie、网络、富文本、图片和链接路由能力。
- 不记录真实通知正文、用户信息或完整 HTML。
- 网络测试不得访问真实 V2EX。
- 保留当前未提交的签到单行布局修正。

---

### 任务一：通知模型与 HTML 解析

**文件：** `Models.kt`、`V2exHtmlParser.kt`、`V2exHtmlParserTest.kt`

- [x] 先写失败测试，覆盖回复通知、感谢通知、富文本正文、引用楼层定位及缺失关键字段。
- [x] 运行 Parser 局部测试确认按预期失败。
- [x] 实现通知模型与 Parser，使局部测试通过。

### 任务二：远端请求、分页和引用补全

**文件：** `V2exRawApi.kt`、`NotificationRemoteDataSource.kt`、`NotificationPagingSource.kt`、`NotificationRepository.kt`、`NotificationRepositoryImpl.kt`、DI 文件及对应测试。

- [x] 先写失败测试，覆盖 URL/登录/受限分类、分页键、末页、取消传播和引用解析降级。
- [x] 运行局部测试确认按预期失败。
- [x] 实现 Retrofit 请求、RemoteDataSource、Pager 和按帖子去重的引用补全，使测试通过。

### 任务三：通知列表、入口 Badge 与导航

**文件：** notification feature、`AccountScreen.kt`、`AccountViewModel.kt`、`MainShell.kt`、`NodeFlowNavHost.kt`、`NodeFlowDestinations.kt` 及对应测试。

- [x] 先写失败测试，覆盖 Badge 文案/消费、通知刷新和带回复楼层的路由。
- [x] 运行局部测试确认按预期失败。
- [x] 实现铃铛入口、分页列表、消息卡片、返回和帖子跳转。
- [x] 为帖子详情增加可选目标楼层，加载后滚动并高亮。

### 任务四：验证与文档

**文件：** `docs/index.md`、相关专题文档。

- [x] 运行全部 JVM 测试、Debug 构建和 Lint。
- [x] 在模拟器安装并检查通知入口、已签到布局、通知列表和返回栈；不执行删除或回复。
- [x] 检查 diff 与敏感信息，完成独立代码审查。
