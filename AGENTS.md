# AGENTS.md

NodeFlow 是 Kotlin + Jetpack Compose + Material 3 的 V2EX Android 客户端，单 `app` 模块，包名 `app.mystery0.nodeflow`。SDK 与依赖版本见 `gradle/libs.versions.toml`，构建配置见 `app/build.gradle.kts`。

## 工作方式

- 沟通、注释、KDoc 和文档使用中文，日志使用英文；交付时简述结果、验证和剩余问题。
- 对明确要求的修改持续完成实现与验证。常规、可逆的实现选择自行决定；只有缺失信息会实质改变结果、涉及未授权的外部操作或不可逆操作时才询问，并先完成不依赖回答的工作。
- 按任务读取下表中相关文档和源码；已在上下文中的内容无需重复加载。源码与构建配置决定实现事实，当前用户要求决定任务范围。
- 技能按实际适用范围使用。通用技能的流程建议不额外增加项目审批、提交或文档要求；如确有阻塞，指出具体文件、原文和所需输入。
- 小改动直接执行；跨层或多阶段任务使用简短计划，只有需要保存设计取舍或交接时才写入 `docs/plans/`。历史计划中的技能调用、审批和提交步骤仅为当时记录，不是当前指令。
- 独立检索可并行。工具支持时，对能独立交付且能减少耗时或提高质量的子任务使用子代理，明确范围与写入文件；主代理负责整合验证。紧密依赖的改动和同一 Gradle 工作目录中的构建串行执行。

## 项目边界

- 数据流：Screen → UiEvent → ViewModel → UseCase → Repository → 数据源。遵守 `core / data / domain / feature` 分层，UI 和 ViewModel 不直接访问网络、Room、DataStore 或 Cookie。
- UI 使用 Compose，遵循 Material 3、Dynamic Color、深色模式和 edge-to-edge；依赖注入使用 Koin，依赖版本由版本目录统一管理。
- 复用现有认证、异常、Parser、链接路由、富文本和图片预览能力。修改 V2EX 请求头或页面分类前核查现有协议与回归测试；登录页、受限页、首页重定向和 Cloudflare 页面不能当作正常业务内容。
- 业务行为变更补充或更新对应测试，网络测试使用替身，不访问真实 V2EX。按[验证矩阵](docs/development/testing.md)选择检查，通过后仅因新增修改、失败或未消除的风险扩大或重复验证。
- Room 结构变更同步 `app/schemas/`，版本升级提供显式 Migration，禁止破坏性迁移；版本 1 开发基线的背景见[存储说明](docs/subsystems/storage.md)。
- 不泄露真实凭据、签名材料或用户隐私内容；日志、异常和 fixture 的具体要求见[工作流](docs/development/workflow.md)。
- 保留用户无关改动；未经要求不提交、推送或创建 PR，不执行破坏性 Git 操作。只报告实际完成的验证，无法执行时说明原因。

## 按需文档

下表是查找入口，只读与当前任务直接相关的部分。代码包位于 `app/src/main/java/app/mystery0/nodeflow/`。

| 任务 | 文档 | 代码入口 |
| --- | --- | --- |
| 分层、工程结构、DI | [总体架构](docs/architecture/overview.md) | `core/`、`data/`、`domain/`、`feature/`、`di/` |
| 开发流程、日志、隐私、Git、构建 | [工作流](docs/development/workflow.md) | `app/build.gradle.kts` |
| 选择验证范围、排查测试失败 | [测试与验证](docs/development/testing.md) | `app/src/test/`、`app/src/androidTest/` |
| 文档、Agent 指令或技能维护 | [文档规范](docs/development/documentation.md) | `AGENTS.md`、`docs/` |
| 网络、登录、签到、通知、访问受限 | [网络与认证](docs/subsystems/network-auth.md) | `core/network/`、`core/parser/`、`data/auth/` |
| Room、缓存、设置、会话 | [存储](docs/subsystems/storage.md) | `core/database/`、`core/datastore/`、`data/` |
| Compose、状态、导航、深链、转场 | [UI 与导航](docs/subsystems/ui-navigation.md) | `feature/`、`navigation/`、`core/designsystem/` |
| HTML、正文、回复、链接、图片 | [内容渲染](docs/subsystems/content-rendering.md) | `core/parser/`、`core/link/`、`core/ui/` |

复杂改动需要理解既有取舍时搜索 `docs/plans/`；排查服务端或页面行为时搜索 `docs/investigations/`。完整索引见 [docs/index.md](docs/index.md)。
