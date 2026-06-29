# NodeFlow

NodeFlow 是一个专注 Android 的现代 V2EX 第三方客户端，使用 Kotlin、Jetpack Compose 和 Material 3 从零构建。

本项目只做 Android 端，不使用 Flutter、Kotlin Multiplatform 或其他跨端方案。

NodeFlow 是非官方第三方客户端，与 V2EX 官方没有从属关系。项目当前处于早期 MVP 阶段，优先保证架构清晰、可编译运行和后续可迭代。

## 技术栈

- Kotlin
- Jetpack Compose
- Material 3 / Material You
- Navigation Compose
- Coroutines + Flow
- Koin
- Retrofit + OkHttp
- kotlinx.serialization
- Room
- DataStore
- Coil
- Paging 3 预留
- WorkManager 预留
- Jsoup
- Gradle Kotlin DSL + Version Catalog

## 当前状态

第一阶段 MVP 已包含：

- 单 Activity + Compose Navigation。
- Material 3 主题、动态颜色、深色模式、edge-to-edge。
- 首页 V2EX 最新主题流。
- 节点主题列表，默认节点为 `python`。
- 主题详情页，包含正文、图片展示、链接点击、回复列表、复制链接和分享入口。
- 用户资料页，展示公开 API 可获得的基础信息。
- 设置页，支持深色模式、动态颜色、清除缓存和关于信息。
- Token / Cookie 登录态存储框架、`AuthInterceptor` 和 `AuthRepository`。
- Room 主题、节点、用户缓存表。
- DataStore 设置和登录态保存。
- 网络错误统一封装。

## 数据来源

第一阶段优先使用 V2EX 仍可匿名访问的旧 JSON API：

- `/api/topics/latest.json`
- `/api/topics/show.json`
- `/api/replies/show.json`
- `/api/nodes/show.json`
- `/api/members/show.json`

节点主题列表使用 `/go/{node}` HTML 页面并通过 Jsoup 解析。API 2.0 Beta 需要 Personal Access Token，本项目已预留 Token 注入能力，完整网页登录流程后续实现。

## 隐私与安全

- 项目不内置任何私有 Token、Cookie、密钥或服务端配置。
- 登录态通过本地 DataStore 保存，并在 Android 系统备份和设备迁移规则中排除。
- 当前版本不会上传用户数据到第三方服务，网络请求仅面向 V2EX 数据源。

## 架构

当前使用单 `app` 模块，并通过包结构预留后续拆模块边界：

- `core.common`
- `core.network`
- `core.database`
- `core.datastore`
- `core.parser`
- `core.designsystem`
- `core.ui`
- `data.*`
- `domain.*`
- `feature.*`

UI 遵循 UDF/MVI 思路：`Screen` 渲染 `UiState` 并发送 `UiEvent`，`ViewModel` 调用 UseCase，UseCase 依赖 Repository 接口，Repository 聚合远端、本地缓存和 DataStore。

## 构建

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Room 编译器已迁移到 KSP。AGP 9 内置 Kotlin 与新 DSL 仍保留为后续单独迁移项，避免和业务功能迭代混在同一次改动中。

## 后续计划

- 完整网页登录流程。
- 回复、发帖和编辑能力。
- 通知列表与 WorkManager 后台刷新。
- Paging 3 分页加载。
- 更完整的用户主页 HTML 解析。
- 将单模块包结构拆分为 Gradle 多模块。
- 更细的 UI 测试和 Repository 测试。

## 许可证

Apache License 2.0。详见 [LICENSE](LICENSE)。
