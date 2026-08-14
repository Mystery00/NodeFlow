# 帖子列表置顶标记实施计划

> **执行要求：** 使用内联 TDD 按步骤实施；每个步骤使用复选框跟踪。

**目标：** 解析、缓存并展示 V2EX 主题列表的置顶状态。

**架构：** Parser 将列表单元的显式标记或服务端时间排序异常映射到 `Topic.isPinned`，Room 迁移持久化该字段，设计系统以共享视觉基座提供不可点击状态 Chip，`TopicListItem` 统一展示。

**技术栈：** Kotlin、Jsoup、Room、Jetpack Compose、Material 3、JUnit 4、Truth、AndroidX MigrationTestHelper。

**设计：** `docs/plans/2026-08-14-topic-pinned-badge-design.md`

## 全局约束

- 与用户沟通、代码注释和文档使用中文，日志使用英文。
- 不因第一条或推广节点本身推断置顶；无显式标记时只识别最长时间倒序子序列之外的最小异常集合，允许 60 秒误差。
- Room 升级必须提供 `MIGRATION_4_5`、迁移测试和版本 5 schema，禁止破坏性迁移。
- 标记不可点击，节点 Chip 的现有导航行为不得改变。

### 任务一：解析并传播置顶状态

**文件：**

- 修改：`app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt`

**接口：**

- 产出：`Topic.isPinned: Boolean`
- 产出：列表单元显式标记与容器内时间顺序反转解析，不误判普通首条主题或其他列表容器。

- [x] 写入显式标签/class/data 标记以及普通首条、推广节点不误判的失败测试。
- [x] 运行 Parser 局部测试，确认缺少 `isPinned` 或断言失败。
- [x] 为 `Topic` 增加默认字段并实现最小显式标记解析。
- [x] 重跑 Parser 局部测试并确认通过。

### 任务二：持久化状态并迁移 Room

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/core/database/entity/TopicEntity.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/database/NodeFlowDatabase.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/database/DatabaseModule.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/data/topic/TopicLocalDataSource.kt`
- 新建：`app/src/test/java/app/mystery0/nodeflow/data/topic/TopicLocalDataSourceTest.kt`
- 新建：`app/src/androidTest/java/app/mystery0/nodeflow/core/database/TopicPinnedMigrationTest.kt`
- 生成：`app/schemas/app.mystery0.nodeflow.core.database.NodeFlowDatabase/5.json`

**接口：**

- 产出：`MIGRATION_4_5`，SQL 为 `ALTER TABLE topics ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0`。
- 产出：`TopicEntity.toTopic()` 与 `Topic.toEntity()` 双向保留状态。

- [x] 写入 Entity 映射失败测试与 4→5 迁移测试。
- [x] 运行 JVM 映射测试并确认缺少 Entity 字段。
- [x] 增加 Entity 字段、版本 5、显式迁移和数据库注册。
- [x] 重跑映射测试，构建并生成版本 5 schema。
- [x] 在模拟器运行迁移测试，确认旧数据保留且默认值为 `false`。

### 任务三：展示不可点击置顶 Chip

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/NodeChip.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/ui/TopicListItem.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/ui/TopicListItemContent.kt`
- 修改：`app/src/test/java/app/mystery0/nodeflow/core/ui/TopicListItemContentTest.kt`
- 新建：`app/src/androidTest/java/app/mystery0/nodeflow/core/designsystem/component/StatusChipTest.kt`

**接口：**

- 产出：`StatusChip(title: String)`，与 `NodeChip` 共用紧凑标签样式且没有点击行为。
- 产出：`topicPinnedChip(topic, label): String?`，仅置顶主题返回资源化文案。

- [x] 写入置顶/普通主题的 UI 内容失败测试。
- [x] 运行 UI 内容局部测试并确认新接口不存在。
- [x] 实现共享视觉基座、不可点击 `StatusChip` 和列表展示。
- [x] 重跑 UI 内容与布局测试并确认通过。

### 任务四：文档、全量验证与独立提交

**文件：**

- 修改：`docs/subsystems/content-rendering.md`
- 修改：`docs/subsystems/storage.md`
- 修改：`docs/subsystems/ui-navigation.md`
- 修改：`docs/index.md`

- [x] 更新 Parser、Room 缓存、列表 UI 说明与文档索引。
- [x] 运行相关局部测试、全部 JVM 单元测试、Debug 构建和 Lint。
- [x] 安装 Debug 包并在模拟器验证主题列表布局；通过测试 fixture 验证置顶视觉与不可点击语义。
- [x] 检查差异、schema、敏感信息与计划覆盖后创建第三个独立提交。
