# 帖子回复作者导航实施计划

> **执行要求：** 使用内联 TDD 按步骤实施；每个步骤使用复选框跟踪。

**目标：** 让帖子详情中每条回复的作者头像和用户名都能进入对应用户详情页。

**架构：** `ReplyItem` 负责声明两个可点击入口并输出用户名，`TopicDetailScreen` 只复用现有 `onUserClick` 导航回调。导航目的地、ViewModel、Repository 和数据模型保持不变。

**技术栈：** Kotlin、Jetpack Compose、Material 3、JUnit 4、Truth、Navigation Compose。

**设计：** `docs/plans/2026-08-14-topic-reply-author-navigation-design.md`

## 全局约束

- 与用户沟通、代码注释和文档使用中文，日志使用英文。
- 不新增 XML layout，不复制用户详情路由。
- 先观察失败测试，再写最小实现。

### 任务一：回复作者导航目标

**文件：**

- 修改：`app/src/test/java/app/mystery0/nodeflow/core/ui/ReplyItemAuthorTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/ui/ReplyItem.kt`

**接口：**

- 输入：`Reply.author.username`
- 输出：`internal fun replyAuthorNavigationTarget(reply: Reply): String?`

- [x] 写入有效用户名与空用户名的失败测试。
- [x] 运行 `ReplyItemAuthorTest`，确认因目标函数不存在而失败。
- [x] 实现用户名清理函数，并为头像、用户名增加点击语义和 `onUserClick` 参数。
- [x] 重跑 `ReplyItemAuthorTest`，确认通过。

### 任务二：详情页接线与验证

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`
- 修改：`docs/subsystems/ui-navigation.md`
- 修改：`docs/index.md`

**接口：**

- 消费：`ReplyItem(onUserClick = (String) -> Unit)`
- 产出：回复头像和用户名调用根导航的用户详情路由。

- [x] 将详情页现有 `onUserClick` 传给每个 `ReplyItem`。
- [x] 更新 UI 导航专题文档和文档索引。
- [x] 运行相关测试、全部 JVM 单元测试与 Debug 构建。
- [x] 在模拟器分别验证回复头像和用户名导航及返回栈。
- [x] 检查差异和敏感信息后创建独立提交。
