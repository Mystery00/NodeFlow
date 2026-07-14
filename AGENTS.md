# AGENTS.md

本文件是 AI Agent 在 NodeFlow 仓库中的首要入口。开始任务前先阅读本文件，再按“任务文档路由”打开与当前问题直接相关的专题文档；不要无目的加载全部文档。用户当前要求优先于本文件，当前源码与构建配置是实现事实的最终依据。

## 项目速览

NodeFlow 是使用 Kotlin、Jetpack Compose 和 Material 3 构建的现代 V2EX Android 第三方客户端，只支持 Android。项目为单 `app` 模块，包名为 `app.mystery0.nodeflow`，最低 SDK 29，Compile/Target SDK 37，JVM target 21。

核心数据流为：

```text
Compose Screen → UiEvent → ViewModel → UseCase → Repository
Repository → RemoteDataSource / LocalDataSource / DataStore → UiState → Screen
```

主要代码入口：

- `core.*`：网络、数据库、DataStore、解析、链接、设计系统和通用能力。
- `data.*`：远端/本地数据源和 Repository 实现。
- `domain.*`：Repository 接口与 UseCase。
- `feature.*`：Screen、UiState、UiEvent 和 ViewModel。
- `navigation`：根导航、主页面 Shell 和目的地。
- `app/src/test/`：JVM 单元测试。
- `app/schemas/`：Room schema。

完整架构参见 [`docs/architecture/overview.md`](docs/architecture/overview.md)。

## 不可违反的规则

- 与用户沟通使用中文；代码注释、KDoc 和项目文档使用中文；日志使用英文。
- UI 使用 Jetpack Compose，不新增 XML layout；遵循 Material 3、Dynamic Color、深色模式和 edge-to-edge。
- 遵守 `core / data / domain / feature` 边界，UI 和 ViewModel 不直接访问网络、Room、DataStore 或 Cookie。
- 依赖注入使用 Koin；依赖版本统一由 `gradle/libs.versions.toml` 管理。
- 修改业务逻辑必须补充或更新对应单元测试；网络测试不得访问真实 V2EX。
- Room 版本变更必须提供 Migration 并更新 `app/schemas/`；禁止破坏性迁移。
- 不在日志、代码、测试、截图、文档或提交中保存真实 Cookie、Token、密码、签名信息和用户隐私数据。
- 不随意修改 V2EX 请求的 User-Agent、Referer、Origin、Cookie 或页面分类规则。
- 不把登录页、受限页、首页重定向或 Cloudflare 页面解析为正常业务内容。
- 优先复用现有 Cookie、异常、Parser、链接路由、富文本和图片预览能力，避免形成第二套实现。
- 保留用户未提交的无关修改，不执行破坏性 Git 操作；未经要求不提交、不推送、不创建 PR。
- 不声称未执行的测试、构建或真机检查已经通过。

## 任务文档路由

| 任务类型 | 必读文档 | 常见代码入口 |
| --- | --- | --- |
| 了解工程结构、调整分层或 DI | [总体架构](docs/architecture/overview.md) | `core/`、`data/`、`domain/`、`feature/`、`di/` |
| 日常开发、日志、隐私、构建或 Git | [开发工作流](docs/development/workflow.md) | `app/build.gradle.kts`、`gradle/libs.versions.toml` |
| 新功能、Bug 修复或测试失败 | [测试与验证](docs/development/testing.md) | `app/src/test/` 与被测代码 |
| 新建、迁移或更新文档 | [文档规范](docs/development/documentation.md) | `docs/index.md`、`docs/` |
| 网络请求、登录、签到、通知、访问受限 | [网络、访问控制与登录](docs/subsystems/network-auth.md) | `core/network/`、`core/parser/`、`data/auth/` |
| Room、缓存、离线回退、设置或会话存储 | [数据库、缓存与设置](docs/subsystems/storage.md) | `core/database/`、`core/datastore/`、`data/*LocalDataSource` |
| Compose 页面、状态、导航、深链或转场 | [UI、导航与状态管理](docs/subsystems/ui-navigation.md) | `feature/`、`navigation/`、`core/designsystem/` |
| HTML、正文、回复、链接、图片或大图预览 | [内容渲染](docs/subsystems/content-rendering.md) | `core/parser/`、`core/link/`、`core/ui/`、`core/designsystem/` |
| 查找历史方案或理解决策背景 | [文档总索引](docs/index.md) | `docs/plans/`、`docs/investigations/` |

进行新需求或复杂修复时，先在 `docs/plans/` 搜索同一子系统的历史设计；排查服务端或页面行为时，再检查 `docs/investigations/`。

## 测试与验证底线

Windows PowerShell 常用命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
```

- 业务逻辑改动：运行相关局部测试和全部 `testDebugUnitTest`。
- Kotlin、资源或构建改动：至少运行 `assembleDebug`。
- Room 改动：运行数据库测试、全部测试和构建，并检查 Migration 与 schema。
- UI、导航、登录、深链、图片和系统行为：在自动化验证外补充模拟器或真机检查。
- 因环境无法执行的验证必须在最终回复中明确说明。

详细测试矩阵参见 [`docs/development/testing.md`](docs/development/testing.md)。

## 文档规则

- `README.md` 只保存应用图标、项目简述、下载、编译、贡献和许可证等对外概要。
- 长期架构放在 `docs/architecture/`，开发规范放在 `docs/development/`，子系统说明放在 `docs/subsystems/`。
- 设计与实施计划统一放在 `docs/plans/`；不再创建 `docs/superpowers/`、`specs/` 或其他计划目录。
- 调查与外部行为验证放在 `docs/investigations/`。
- 新文档使用中文；专题文档采用稳定英文文件名，设计/计划/调查采用 `YYYY-MM-DD-主题[-design].md`。
- 新增、移动或删除文档后更新 `docs/index.md`；影响任务查找时同步更新本文件的路由表。

完整规范参见 [`docs/development/documentation.md`](docs/development/documentation.md)。

## 完成前检查

- 是否阅读了任务对应的专题文档和相关历史计划？
- 是否保持架构边界并复用现有能力？
- 是否补充或更新了业务逻辑测试？
- 是否检查 Cookie、Token、用户内容和签名信息等敏感数据？
- Room 变更是否包含 Migration 和 schema？Koin 是否注册新增依赖？
- 注释和文档是否为中文，日志是否为英文？
- 是否执行对应测试、构建和必要的真机验证？
- 文档索引与链接是否同步更新？
