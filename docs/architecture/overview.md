# 总体架构

## 工程形态

NodeFlow 包含两个 Gradle 模块：`:app` 为 Android 应用，包根路径为 `app.mystery0.nodeflow`；`:image-hosting` 为 Kotlin/JVM 图床适配模块，包根路径为 `app.mystery0.nodeflow.imagehosting`。模块注册以 `settings.gradle.kts` 为准。app 内部通过包结构组织分层，不要求每个 feature 都独立成 Gradle 模块。

## 分层与数据流

```text
Compose Screen
  → UiEvent
ViewModel
  → UseCase
Domain Repository 接口
  → Data Repository 实现
  ├─ RemoteDataSource：JSON API、HTML 页面、登录接口
  ├─ LocalDataSource：Room 缓存
  └─ DataStore / CookieStorage：设置与会话
  → UiState
Compose Screen
```

- UI 层渲染状态并派发交互，不直接访问业务网络、Room、DataStore 或 Cookie；图片展示通过 Coil 组件加载，系统选图、浏览器和分享交互由 UI/导航层承接。
- ViewModel 主要通过 UseCase 协调状态，不包含请求构造、HTML 解析和数据库细节；账号退出等现有路径直接调用 domain Repository 接口，并非每个动作都有单独 UseCase。
- UseCase 表达具有业务语义的独立动作；没有业务规则的简单转发不必强行封装。
- Domain 层定义 Repository 接口和领域模型，不依赖具体 UI 与数据源实现。
- Repository 决定远端、本地缓存和持久化数据的组合策略，并完成模型转换。
- RemoteDataSource 处理请求协议，LocalDataSource 封装 DAO 调用。

## 包职责

- `core.common`：通用异常、Dispatcher 等基础能力。
- `core.network`：OkHttp、Retrofit、Cookie、鉴权、User-Agent 和访问保护。
- `core.database`：Room Database、DAO 和 Entity；当前数据库版本为 1，没有历史 Migration，版本变化时按存储说明提供迁移。
- `core.datastore`：设置与会话状态持久化。
- `core.parser`：V2EX HTML 页面分类与解析。
- `core.link`：V2EX 链接识别。
- `core.designsystem`：主题、通用 Compose 组件和富文本展示。
- `core.ui`：跨功能复用的业务 UI 与格式化逻辑。
- `data.*`：DataSource 与 Repository 实现。
- `domain.*`：Repository 接口和 UseCase。
- `feature.*`：Screen、UiState、UiEvent 和 ViewModel。
- `navigation`：目的地、根导航和主页面 Shell。
- `image-hosting`：独立 Kotlin/JVM 图床契约、descriptor 注册表和 V2EX/Imgur 适配器；app 通过 `V2exImageTransportImpl` 桥接 V2EX 会话，`ImgurHttpImageHostAdapter` 使用独立 OkHttp 客户端实现匿名网页 HTTP 上传流程。挑战页返回手动网页恢复动作，具体错误与验证边界见[网络说明](../subsystems/network-auth.md)。

## 依赖注入

app 统一使用 Koin，由 `di/NodeFlowModules.kt` 汇总 core、data、domain 和 feature 的模块。新增依赖注册到职责对应的模块文件；`:image-hosting` 使用构造函数注入，不依赖 Koin。新增抽象前先确认它具有明确边界或复用价值。

## 图床上传边界

回复编辑器只依赖 `ImageHostId`、`ImageHostDescriptor` 和结构化上传结果，不按 provider 编写协议分支。应用从设置读取稳定 host ID，通过 `ImageHostRegistry` 发现 descriptor，并将图片上传结果映射为带命名空间的草稿图片 ID。`:image-hosting` 不依赖 Android、Compose、Koin 或 app 包。

## 技术栈

核心技术为 Kotlin、Jetpack Compose、Material 3、Navigation Compose、Coroutines/Flow、Koin、Retrofit/OkHttp、kotlinx.serialization、Room/KSP、DataStore、Paging 3、WorkManager、Jsoup、Coil 和 ZoomImage。版本以 `gradle/libs.versions.toml` 为准。
