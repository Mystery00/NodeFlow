# AGENTS.md

NodeFlow 是 Kotlin + Jetpack Compose + Material 3 的 V2EX Android 客户端，包含 Android `app` 模块和 Kotlin/JVM `image-hosting` 模块，应用包名为 `app.mystery0.nodeflow`。模块注册见 `settings.gradle.kts`，SDK 与依赖版本见 `gradle/libs.versions.toml`，构建配置见各模块的 `build.gradle.kts`。

## 工作方式

- 沟通、注释、KDoc 和文档使用中文，日志使用英文；交付时简述结果、验证和剩余问题。
- 按任务读取下表中的相关文档和源码。源码与构建配置决定实现事实，当前用户要求决定任务范围；`docs/plans/` 中的历史方案和验收记录不自动代表现状或当前指令。
- 需要保存设计取舍或交接时才新增 `docs/plans/` 文档；局部修改直接更新受影响的现行说明。
- 同一 Gradle 工作目录中的构建串行执行，避免争用输出。

## 项目边界

- 遵守 `core / data / domain / feature` 分层。Screen 通过 UiEvent 驱动 ViewModel；ViewModel 通常调用 UseCase，也可调用领域 Repository 接口，不绕过领域边界直接访问业务数据源。UI 和 ViewModel 不直接访问业务网络、Room、DataStore 或 Cookie；Coil 图片加载组件属于正常 UI 用法。
- UI 使用 Compose，遵循 Material 3、Dynamic Color、深色模式和 edge-to-edge；app 依赖注入使用 Koin，依赖版本由版本目录统一管理。`:image-hosting` 使用构造函数注入，不依赖 Android、Compose、Koin 或 app 模块。
- 复用现有认证、异常、Parser、链接路由、富文本和图片预览能力。修改 V2EX 请求头或页面分类前核查现有协议与回归测试；登录页、受限页、首页重定向和 Cloudflare 页面不能当作正常业务内容。
- 业务行为变更补充或更新对应测试。自动化网络测试使用替身，不访问真实 V2EX 或图床；真实服务验证另行明确范围。按[验证矩阵](docs/development/testing.md)选择检查，通过后仅因新增修改、失败或未消除的风险扩大或重复验证。
- Room 结构变更同步 `app/schemas/`，版本升级提供显式 Migration，禁止破坏性迁移；版本 1 开发基线的背景见[存储说明](docs/subsystems/storage.md)。
- 不泄露真实凭据、签名材料或用户隐私内容；日志、异常和 fixture 的具体要求见[工作流](docs/development/workflow.md)。
- 保留用户无关改动；未经要求不提交、推送或创建 PR，不执行破坏性 Git 操作。只报告实际完成的验证，无法执行时说明原因。

## 按需文档

下表是查找入口，只读与当前任务直接相关的部分。app 包内相对路径基于 `app/src/main/java/app/mystery0/nodeflow/`；图床模块源码位于 `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/`。

| 任务 | 文档 | 代码入口 |
| --- | --- | --- |
| 分层、工程结构、DI | [总体架构](docs/architecture/overview.md) | `core/`、`data/`、`domain/`、`feature/`、`di/` |
| 开发流程、日志、隐私、Git、构建 | [工作流](docs/development/workflow.md) | `settings.gradle.kts`、各模块 `build.gradle.kts` |
| Google Play 商品详情、素材、发布检查 | [Google Play 发布准备](docs/development/google-play.md) | `app/build.gradle.kts`、`app/src/main/AndroidManifest.xml` |
| 选择验证范围、排查测试失败 | [测试与验证](docs/development/testing.md) | `app/src/test/`、`app/src/androidTest/`、`image-hosting/src/test/` |
| 文档、Agent 指令或技能维护 | [文档规范](docs/development/documentation.md) | `AGENTS.md`、`docs/` |
| 网络、登录、签到、通知、访问受限 | [网络与认证](docs/subsystems/network-auth.md) | `core/network/`、`core/parser/`、`data/auth/` |
| 图床上传、适配器与 provider 契约 | [总体架构](docs/architecture/overview.md)、[网络与认证](docs/subsystems/network-auth.md) | `image-hosting/src/main/`、`data/reply/`、`feature/replyeditor/` |
| Room、缓存、设置、会话 | [存储](docs/subsystems/storage.md) | `core/database/`、`core/datastore/`、`data/` |
| Compose、状态、导航、深链、转场 | [UI 与导航](docs/subsystems/ui-navigation.md) | `feature/`、`navigation/`、`core/designsystem/` |
| HTML、正文、回复、链接、图片 | [内容渲染](docs/subsystems/content-rendering.md) | `core/parser/`、`core/link/`、`core/ui/` |

复杂改动需要理解既有取舍时搜索 `docs/plans/`；排查服务端或页面行为时搜索 `docs/investigations/`。完整索引见 [docs/index.md](docs/index.md)。
