# 总体架构

## 工程形态

NodeFlow 当前是单 `app` 模块 Android 应用，包根路径为 `app.mystery0.nodeflow`。项目通过包结构维持 Clean Architecture 边界，为后续按稳定边界拆分 Gradle 模块预留空间；不要仅为形式拆模块。

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

- UI 层渲染状态并派发交互，不直接访问网络、Room、DataStore 或 Cookie。
- ViewModel 协调状态与 UseCase，不包含请求构造、HTML 解析和数据库细节。
- UseCase 表达具有业务语义的独立动作；没有业务规则的简单转发不必强行封装。
- Domain 层定义 Repository 接口和领域模型，不依赖具体 UI 与数据源实现。
- Repository 决定远端、本地缓存和持久化数据的组合策略，并完成模型转换。
- RemoteDataSource 处理请求协议，LocalDataSource 封装 DAO 调用。

## 包职责

- `core.common`：通用异常、Dispatcher 等基础能力。
- `core.network`：OkHttp、Retrofit、Cookie、鉴权、User-Agent 和访问保护。
- `core.database`：Room Database、DAO、Entity 和 Migration。
- `core.datastore`：设置与会话状态持久化。
- `core.parser`：V2EX HTML 页面分类与解析。
- `core.link`：V2EX 链接识别。
- `core.designsystem`：主题、通用 Compose 组件和富文本展示。
- `core.ui`：跨功能复用的业务 UI 与格式化逻辑。
- `data.*`：DataSource 与 Repository 实现。
- `domain.*`：Repository 接口和 UseCase。
- `feature.*`：Screen、UiState、UiEvent 和 ViewModel。
- `navigation`：目的地、根导航和主页面 Shell。

## 依赖注入

统一使用 Koin。新增依赖应注册到职责对应的 `NodeFlowModules.kt`、`DataSourceModule.kt`、`RepositoryModule.kt`、`DomainModule.kt`、`FeatureModule.kt` 或 core module。新增抽象前先确认它具有明确边界或复用价值。

## 技术栈

核心技术为 Kotlin、Jetpack Compose、Material 3、Navigation Compose、Coroutines/Flow、Koin、Retrofit/OkHttp、kotlinx.serialization、Room/KSP、DataStore、Paging 3、WorkManager、Jsoup、Coil 和 ZoomImage。版本以 `gradle/libs.versions.toml` 为准。
