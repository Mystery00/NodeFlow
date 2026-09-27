# 图床适配模块 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立独立的 Kotlin/JVM 图床适配模块，迁移 V2EX 图片上传并接入经协议验证的 Imgur 匿名网页上传，同时让回复编辑器通过注册表支持后续图床扩展。

**Architecture:** `:image-hosting` 只依赖 Kotlin/JVM、协程、OkHttp、Jsoup 和序列化库，暴露稳定的图床契约、注册表及 V2EX/Imgur 适配器。Android app 保留 URI 读取、DataStore、Koin、Room、草稿映射和 UI，通过窄传输桥接为适配器提供网络会话；回复编辑器只依赖 provider ID、descriptor、上传结果和恢复动作，不按图床写协议分支。

**Tech Stack:** Kotlin 2.4.20、JVM 21、Gradle Kotlin DSL、OkHttp 5.5.0、Jsoup 1.23.2、kotlinx.coroutines 1.11.0、kotlinx.serialization 1.11.0、Koin 4.2.2、Jetpack Compose、DataStore、Room、MockWebServer。

**Spec:** `docs/plans/2026-09-24-image-hosting-adapters-design.md`

## Global Constraints

- `:image-hosting` 不得依赖 Android SDK、Compose、Koin、Room、Retrofit 或任何 `app.*` 包。
- 公共契约使用开放的稳定 `ImageHostId` 字符串，不使用封闭 provider enum；重复注册 ID 必须报配置错误，未知 ID 不得静默回退到默认图床。
- 应用读取图片最多 6 MiB；适配器能力上限与应用上限取较小值，只允许经验证的 PNG/JPEG/GIF/WebP。
- 所有适配器必须透传 `CancellationException`；文件发送后不得因不确定结果自动重放整个上传流程。
- V2EX 使用既有认证客户端和 Cookie 会话；Imgur 使用独立内存 CookieJar/OkHttpClient，绝不共享 V2EX Cookie、Token、AuthInterceptor 或客户端 builder。
- Imgur 只有在取得脱敏网页请求证据并证明原生 HTTP 可复现后才能实现自动上传；失败时实现 `InteractionRequired`/禁用状态和手动网页回退，不猜测 endpoint、不注册官方 API。
- 现有 Room schema 不变；旧的无冒号图片 ID 按 V2EX ID 兼容读取，新 ID 使用 `<hostId>:<remoteId>`，所有规范化和去重集中在 codec。
- 不提交、推送或创建 PR；不删除或重置用户无关改动。每项验证只报告实际执行结果。

---

### Task 1: 验证 Imgur 网页协议并冻结实现门槛

**Files:**
- Create: `docs/investigations/2026-09-24-imgur-web-upload.md`（仅在取得真实脱敏证据后创建）
- Modify: `docs/index.md`（调查报告创建后加入索引）
- Reference: `docs/plans/2026-09-24-image-hosting-adapters-design.md:7.4`

**Interfaces:**
- Produces: 一份不含 Cookie、Token、原始 HTML、用户图片或完整响应正文的协议记录；明确请求顺序、主机/路径、方法、编码、临时会话信息、成功直链格式、失败分类和 Android OkHttp 可复现性。
- Produces: 二选一的冻结结论：`native-http-supported` 允许后续实现 `ImgurWebImageHostAdapter`；或 `interaction-required`，后续只实现不可用状态和手动回退。

- [ ] **Step 1: 使用无隐私样例在当前匿名网页流程中获取证据**

记录从 `https://imgur.com/upload` 打开、选择图片到成功/失败的完整请求顺序。只保留人工替换的 cookie/token 值，例如 `<SESSION_COOKIE>`；不把真实值写入文件、终端日志或 Git。

- [ ] **Step 2: 检查成功结果是否是可展示图片直链**

分别验证 JPEG、PNG、GIF、WebP 的返回媒体类型、直链主机、是否发生 GIFV/视频转换，并确认 V2EX 回复中的图片渲染行为。展示页 URL 不能作为成功直链。

- [ ] **Step 3: 记录失败与不确定结果**

覆盖页面登录墙、验证页、429/限流、普通服务错误、上传后断网和响应结构变化，给每种情况标注 `NotSubmitted`、`Rejected` 或 `Unknown`。

- [ ] **Step 4: 判断原生 HTTP 是否能复现**

用脱敏 fixture 和一次受控的 Android/OkHttp 小样例验证页面脚本是否提供 HTTP 流程所需的上下文。若必须运行不可复现的脚本或用户交互，选择 `interaction-required`，不要继续猜测协议。

- [ ] **Step 5: 写调查报告并执行隐私检查**

只有 `native-http-supported` 或 `interaction-required` 结论已经有证据时才创建调查报告。运行：

```powershell
rg -n "Cookie|Authorization|<SESSION|<TOKEN|完整响应|原始 HTML" docs/investigations/2026-09-24-imgur-web-upload.md
```

Expected: 只出现脱敏字段名、禁止记录说明或人工占位符，不出现真实凭据/用户内容；随后更新 `docs/index.md`。

**Gate:** 若结论为 `interaction-required`，跳过 Task 5 中的自动 Imgur 协议实现，仅实现 descriptor 的不可用状态、`InteractionRequired` 错误和外部网页手动回退；其余 registry、V2EX、app 选择和草稿任务仍可继续。

---

### Task 2: 建立 `:image-hosting` Kotlin/JVM 模块与公共契约

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `image-hosting/build.gradle.kts`
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/contract/ImageHostContract.kt`
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/registry/ImageHostRegistry.kt`
- Create: `image-hosting/src/test/kotlin/app/mystery0/nodeflow/imagehosting/registry/ImageHostRegistryTest.kt`

**Interfaces:**
- Produces: `ImageHostId(value: String)`, `ImageHostDescriptor`, `ImageHostCapabilities`, `UploadImage`, `UploadedImage`, `UploadFailure`, `UploadResult`, `RecoveryAction`, `ImageHostAdapter`, `ImageHostRegistry`。
- `ImageHostAdapter.upload(image: UploadImage): UploadResult`；`ImageHostRegistry.descriptors(): List<ImageHostDescriptor>`；`ImageHostRegistry.find(id: ImageHostId): ImageHostAdapter?`。
- `UploadFailure` 必须携带 host、分类、请求阶段、结果确定性、可选 retry 时间和 `RecoveryAction`，不携带原始响应正文。

- [ ] **Step 1: 注册 JVM 插件和模块依赖**

在版本目录增加 `kotlin-jvm` 插件别名，`settings.gradle.kts` 增加 `include(":image-hosting")`，模块使用 JVM 21，与 app 共用版本目录中的 Kotlin/OkHttp/Jsoup/协程/序列化版本。模块测试只声明 JUnit、Truth、协程测试和 MockWebServer。

- [ ] **Step 2: 编写契约测试**

测试 `ImageHostId` 拒绝空白值、registry 保持 descriptor 顺序、重复 ID 抛配置错误、未知 ID 返回 null、能力校验拒绝不支持 MIME 和超过适配器上限的输入，并断言 `CancellationException` 不被转成 `UploadFailure`。

- [ ] **Step 3: 实现最小契约和 registry**

registry 构造时按 ID 检查唯一性；`find` 只查找完全相等的稳定 ID，不自动替换为默认 provider。公共模型不出现 `qqfile`、`once`、`sid`、`deletehash`、Android URI 或领域 `UploadedReplyImage`。

- [ ] **Step 4: 运行模块测试和依赖边界检查**

运行：

```powershell
.\\gradlew.bat :image-hosting:test
.\\gradlew.bat :image-hosting:dependencies
```

Expected: 模块测试通过；依赖树不出现 Android、Compose、Koin、Room、Retrofit 或 `project :app`。

---

### Task 3: 将 V2EX 图片协议迁移为模块适配器

**Files:**
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/v2ex/V2exImageTransport.kt`
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/v2ex/V2exImageHostAdapter.kt`
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/v2ex/V2exImageParser.kt`
- Create: `image-hosting/src/test/kotlin/app/mystery0/nodeflow/imagehosting/v2ex/V2exImageHostAdapterTest.kt`
- Create: `image-hosting/src/test/kotlin/app/mystery0/nodeflow/imagehosting/v2ex/V2exImageParserTest.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/reply/V2exImageRemoteDataSource.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/DataSourceModule.kt`

**Interfaces:**
- Produces module-local `V2exImageTransport.getUploadPage(): V2exHttpResponse` and `uploadImage(fileName, mimeType, bytes, referer): V2exHttpResponse`。
- Produces `V2exPageAccessClassifier` bridge states for available/login/challenge/unrecognized pages; module adapter converts them to public `UploadResult`。
- Consumes existing V2EX `qqfile` multipart behavior, trusted-origin rules, parser fixtures and authentication client; does not consume `V2exWriteApi` directly。

- [ ] **Step 1: 把现有页面和 JSON fixture 测试迁入模块**

从 `V2exHtmlParserTest` 中迁移图片上传页、登录页、挑战页、成功 JSON、额度/权限和非可信图片 host 用例；fixture 只保留最小脱敏 HTML/JSON。先在模块测试中锁定旧结果，再删除 app 中重复的图片专用 parser 分支。

- [ ] **Step 2: 定义 V2EX 窄传输和页面分类桥接**

在模块定义设计文档中的 `V2exImageTransport`、`V2exHttpResponse`、`V2exPageAccessClassifier` 及精确状态类型。app bridge 使用现有 `V2exWriteApi` 调用 GET `/i/upload` 和 `qqfile` POST，读取并关闭 Retrofit response body，再返回模块响应。

- [ ] **Step 3: 编写适配器失败矩阵测试**

使用 fake transport 覆盖：可信 `/i/upload` 页面成功、`/signin` 登录、访问挑战、站外重定向、上传后 `/signin`、非可信响应、额度、权限、JSON 解析失败、发送后异常和取消。断言发送后异常为 `Unknown`/`UploadUnconfirmed`，不触发第二次上传。

- [ ] **Step 4: 实现 `V2exImageHostAdapter`**

保留原有 `qqfile`、Referer、一次性 body、来源检查和图片 URL host 白名单；成功结果填充 `UploadedImage(hostId="v2ex", remoteId, directUrl, displayPageUrl, actualMimeType)`，不把 app 领域模型带入模块。

- [ ] **Step 5: 接回 app 并删除旧数据源链路**

以 bridge/adapter 替换 `V2exImageRemoteDataSource` 的 Koin 注册和 repository lambda；迁移回归通过后删除旧类及其仅用于图片上传的 app parser 方法。保留回复/感谢使用的 `V2exWriteApi` 方法不变。

- [ ] **Step 6: 运行 V2EX 相关验证**

运行：

```powershell
.\\gradlew.bat :image-hosting:test
.\\gradlew.bat :app:testDebugUnitTest --tests 'app.mystery0.nodeflow.core.parser.V2exHtmlParserTest'
.\\gradlew.bat :app:testDebugUnitTest --tests 'app.mystery0.nodeflow.data.reply.ImageUploadRepositoryImplTest'
.\\gradlew.bat :app:assembleDebug
```

Expected: 迁移前已有 V2EX 图片页/响应行为保持一致，模块和 app 构建通过。

---

### Task 4: 重构 app 上传 Repository、错误映射与草稿 codec

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/reply/ImageUploadRepositoryImpl.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyRepositories.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyUseCases.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyModels.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyImageIdCodec.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/database/entity/ReplyDraftEntity.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/data/reply/ImageUploadRepositoryImplTest.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/data/reply/ReplyImageIdCodecTest.kt`

**Interfaces:**
- `ImageUploadRepository.upload(hostId: ImageHostId, contentUri: String): ImageUploadResult` 固定一次请求的 provider；缺失 host 返回 `HostUnavailable`，不回退。
- `ReplyImageIdCodec.parse(raw: String): NamespacedImageId`：无冒号旧值解析为 `v2ex`，新值按第一个冒号分隔，空 host/remote 拒绝；`format(hostId, remoteId)` 生成规范 ID。
- app 将模块 `UploadedImage` 映射为 `UploadedReplyImage`；缺少 display page 时用 direct URL 填充现有非空 `detailUrl`，URL 必须来自适配器已校验结果。

- [ ] **Step 1: 先补取消和 provider 失败测试**

为 reader 抛出 `CancellationException` 的场景增加测试，期望异常继续抛出；为未知 host、超限 MIME、6 MiB+1、适配器 `Unknown` 结果增加测试，期望错误分类和确定性保持不变。

- [ ] **Step 2: 实现 codec 和命名空间持久化映射**

实现集中式 parse/format/normalize/deduplicate；不要在 Room entity、ViewModel 或 Composable 中手写 `split(":")`。旧无冒号 ID 加载时解释为 V2EX，保存新上传图片时使用 `v2ex:<remoteId>` 或 `imgur:<remoteId>`；不改 Room schema，不批量回写旧记录。

- [ ] **Step 3: 改造 repository 和领域接口**

repository 从 registry 查 adapter，按 descriptor 能力校验 `UploadImage`，映射公共错误到 app UI 可用结果；`ImageUploadRepositoryImpl` 的读取异常按 `CancellationException`、`UnsupportedType`、其他读取错误顺序处理，不再用 `catch(Throwable)` 吞取消。

- [ ] **Step 4: 更新 Room 映射和去重测试**

保持 `ReplyDraftImageEntity` 字段不变，确保旧 V2EX ID、新 namespaced ID、同 remote ID 跨 provider、同 provider 重复 ID均能按 codec 规则保存/加载/去重。若实现选择改变实体字段，停止并先补 schema/migration 设计，不得在本任务中破坏性改库。

- [ ] **Step 5: 运行 Repository/codec 测试**

运行：

```powershell
.\\gradlew.bat :app:testDebugUnitTest --tests 'app.mystery0.nodeflow.data.reply.ImageUploadRepositoryImplTest'
.\\gradlew.bat :app:testDebugUnitTest --tests 'app.mystery0.nodeflow.data.reply.ReplyImageIdCodecTest'
```

Expected: provider 选择、能力校验、取消传播和旧草稿兼容全部通过。

---

### Task 5: 在协议门槛满足时实现 Imgur 适配器（否则实现禁用状态）

**Files:**
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/imgur/ImgurWebImageHostAdapter.kt`
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/imgur/ImgurWebSession.kt`
- Create: `image-hosting/src/main/kotlin/app/mystery0/nodeflow/imagehosting/imgur/ImgurWebResponseParser.kt`
- Create: `image-hosting/src/test/kotlin/app/mystery0/nodeflow/imagehosting/imgur/ImgurWebImageHostAdapterTest.kt`
- Create: `image-hosting/src/test/resources/imgur/*.html`
- Create: `image-hosting/src/test/resources/imgur/*.json`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/network/NetworkModule.kt`

**Interfaces:**
- `ImgurWebImageHostAdapter` 实现 `ImageHostAdapter`，descriptor 固定 `ImageHostId("imgur")`，匿名能力仅列入协议调查实际验证通过的 MIME/大小。
- 内部 `ImgurWebSession` 只在一次 upload 调用中持有独立 CookieJar、OkHttpClient、串行 Mutex 和共享 90 秒 deadline；不向 app/UI 暴露 token、Cookie、HTML 或网页 response。
- 若 Task 1 结论为 `interaction-required`，adapter 不发送图片，返回 `InteractionRequired` + `OpenHostPage`，并通过测试证明不会创建或复用 V2EX client。

- [ ] **Step 1: 将调查证据转换为脱敏 fixture**

只把请求方法、路径、必要字段名、人工替换 token、成功/失败响应结构写入测试资源；删除 Cookie 值、原始 HTML、用户图片数据和完整响应中的隐私字段。fixture 必须能在 MockWebServer 中复现完整准备→上传→完成顺序。

- [ ] **Step 2: 编写安全边界测试**

覆盖正常成功、登录墙、验证页、429、协议结构变化、准备阶段网络失败、发送后超时、非白名单重定向、返回展示页但无图片直链、GIFV/视频结果和取消。断言：不发送 V2EX Cookie、不向非白名单 host 发送图片、发送后 Unknown 不自动重试。

- [ ] **Step 3: 实现独立会话和总 deadline**

创建与 V2EX 完全独立的 OkHttpClient/CookieJar；关闭自动重试和非预期重定向；连接 15 秒、写 60 秒、读 30 秒，总流程共享 90 秒 deadline；使用可取消 Mutex 串行准备和上传。

- [ ] **Step 4: 实现协议 parser 和结果映射**

只解析 Task 1 已证明的字段和阶段；验证最终媒体 URL 的主机、HTTPS、实际 MIME 和直链形态。页面结构变化映射 `ProtocolChanged`，登录墙/验证映射 `InteractionRequired`，发送后解析失败映射 `Unknown`。

- [ ] **Step 5: 运行模块 Imgur 测试**

运行：

```powershell
.\\gradlew.bat :image-hosting:test --tests '*ImgurWebImageHostAdapterTest'
```

Expected: 所有测试只访问 MockWebServer；不允许测试代码请求真实 Imgur。

---

### Task 6: 接入 registry、Koin、独立网络和图床设置

**Files:**
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/DataSourceModule.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/RepositoryModule.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/datastore/SettingsStore.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/model/AppSettings.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/settings/SettingsRepository.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/settings/SettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/settings/UpdateSettingsUseCase.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/test/java/app/mystery0/nodeflow/feature/settings/SettingsViewModelTest.kt`

**Interfaces:**
- DataStore key `reply_image_host`，默认 `v2ex`；未知/移除 ID 在选择器显示需要重新选择，不静默换默认。
- Koin 提供 `ImageHostRegistry`、V2EX bridge/adapter、独立 Imgur client（仅在 Task 1 允许时）和 provider-aware `ImageUploadRepository`。
- 设置层只保存稳定 ID，不保存 adapter 实例、Cookie、token 或 URL。

- [ ] **Step 1: 添加 app 对模块的单向依赖**

在 app 使用 `implementation(project(":image-hosting"))`，检查 app 可访问模块公共契约，而模块构建脚本没有反向依赖。

- [ ] **Step 2: 扩展设置模型和 DataStore**

在 `AppSettings` 增加 `replyImageHost: ImageHostId` 或等价稳定值，`SettingsStore` 读取 `reply_image_host` 时只接受非空字符串，缺失默认 `v2ex`；增加写入方法并通过 SettingsRepository/UseCase 暴露。

- [ ] **Step 3: 注册 adapters 和独立客户端**

在 Koin composition root 构造 V2EX bridge 与 adapter列表，registry 校验重复 ID；Imgur client 不从 V2EX client `newBuilder()` 派生。若 Task 1 为 interaction-required，注册 descriptor 为不可自动上传状态而不是伪造可用 adapter。

- [ ] **Step 4: 添加设置页选择器和资源文案**

在设置页使用 registry descriptor 展示图床选择，文案说明 Imgur 图片发送给第三方且清草稿不会删除远端图片；所有新增文本放 string resources，提供 TalkBack 内容描述和未知 provider 的错误状态。

- [ ] **Step 5: 运行设置和构建检查**

运行：

```powershell
.\\gradlew.bat :app:testDebugUnitTest --tests 'app.mystery0.nodeflow.feature.settings.SettingsViewModelTest'
.\\gradlew.bat :app:assembleDebug
```

Expected: 默认 V2EX、设置持久化、未知值处理和 Koin 图构建通过。

---

### Task 7: 改造回复编辑器为能力驱动的图床选择和恢复流程

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorUiState.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorBottomSheet.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorText.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorViewModelTest.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorTextTest.kt`
- Modify: `app/src/androidTest/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorBottomSheetTest.kt`

**Interfaces:**
- UiState 仅保存 descriptor 列表、当前 host ID、上传状态、结构化 `RecoveryAction` 和消息；ViewModel 不包含 `when(hostId)` 上传协议分支。
- `ReplyEditorEffect` 将 `OpenGallery` 扩展为带 host/action 的事件；`OpenHostPage` 由 app 导航层映射，模块不启动浏览器。
- 上传前固定 requestId、hostId、topicId、editVersion；成功后插入 `UploadedImage.directUrl` 并持久化 namespaced 草稿图片。

- [ ] **Step 1: 补 ViewModel 行为测试**

覆盖匿名用户选择 Imgur 不触发 V2EX 登录、V2EX adapter 的 AuthenticationRequired 才触发登录、上传期间禁用正文/选择/切换/发布/清空、取消和关闭不产生上传、过期结果不复活已清空草稿、UploadUnconfirmed/InteractionRequired 显示正确 host 恢复动作。

- [ ] **Step 2: 实现图床状态和选择事件**

初始化从设置读取 host ID 与 registry descriptors；当前 provider 不存在时显示重新选择状态。切换 provider 只在非上传状态更新设置；选图取消不启动任务，上传期间拒绝切换。

- [ ] **Step 3: 移除全局 V2EX 登录前置拦截**

`ImageSelected` 使用当前 host 调用 `UploadImageUseCase`；只有返回的 V2EX `AuthenticationRequired` 才调用 `RequestLogin`。`Submit` 仍要求 V2EX 登录，确保 Imgur 上传不改变回复发布权限。

- [ ] **Step 4: 实现结构化错误恢复和通用文案**

把 `OpenGallery` 改为 provider/action 数据；V2EX `OpenHostPage` 指向图库，Imgur `OpenHostPage` 指向匿名上传页或手动回退。清空确认文案改为“不会删除已上传到图床的图片”，不写死 V2EX。

- [ ] **Step 5: 加入并发保护和草稿持久化边界**

`ClearConfirmed` 在上传中直接拒绝或保持对话框并提示；成功插入 URL 后先保留 UI 直链，再保存草稿；保存失败提示“图片已上传，但本地草稿保存失败”，不重传。关闭 BottomSheet 只 flush 草稿，不取消 ViewModel 仍存活的上传任务。

- [ ] **Step 6: 运行回复编辑器回归测试**

运行：

```powershell
.\\gradlew.bat :app:testDebugUnitTest --tests 'app.mystery0.nodeflow.feature.replyeditor.ReplyEditorViewModelTest'
.\\gradlew.bat :app:testDebugUnitTest --tests 'app.mystery0.nodeflow.feature.replyeditor.ReplyEditorTextTest'
```

Expected: 光标独立行插入、草稿保存、混合 provider 图片、错误恢复和并发保护通过。

---

### Task 8: 扩展测试、文档和最终验收

**Files:**
- Modify: `app/src/test/java/app/mystery0/nodeflow/core/link/ImageHostMatcherTest.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/data/reply/ImageContentReaderTest.kt`
- Modify: `docs/architecture/overview.md`
- Modify: `docs/subsystems/network-auth.md`
- Modify: `docs/subsystems/storage.md`
- Modify: `docs/subsystems/ui-navigation.md`
- Modify: `docs/index.md`

**Interfaces:**
- Produces: 第三个测试 adapter 注册后可在选择器显示、能力校验和上传，不修改编辑器上传控制流的扩展性证明。
- Produces: 文档只描述已经实现且已验证的模块、网络隔离、设置 key、草稿兼容和 Imgur 协议状态。

- [ ] **Step 1: 增加第三 adapter contract test**

创建仅测试使用的 `test-host` adapter，注册后断言 descriptor 自动显示、registry 能找到、repository 能上传；测试不得新增编辑器 provider 分支。

- [ ] **Step 2: 回归链接和内容能力**

覆盖 `i.imgur.com` 现有 matcher/预览行为、适配器允许 MIME 与 Android reader 签名校验的交集、旧 V2EX 图片和新 namespaced ID 的混合草稿。

- [ ] **Step 3: 更新长期文档和索引**

只在实现和测试完成后更新架构、网络、存储、UI 文档；Imgur 若未通过原生 HTTP 门槛，明确写为不可用/需交互，不写成已支持自动上传。

- [ ] **Step 4: 执行必要的完整验证**

按验证矩阵运行：

```powershell
.\\gradlew.bat :image-hosting:test
.\\gradlew.bat :app:testDebugUnitTest
.\\gradlew.bat :app:assembleDebug
.\\gradlew.bat :app:lintDebug
```

若有连接设备，再运行相关编辑器设备测试或 `:app:connectedDebugAndroidTest`；真实设备验收覆盖 V2EX 登录/上传、Imgur 当前结论允许的流程、网络断开、取消、旋转、退出页面、进程恢复、深色模式和无障碍文案。不能把未连接设备描述为通过。

- [ ] **Step 5: 做最终差异和隐私检查**

运行 `git diff --check`、检查新增文件路径/相对链接、确认没有 Cookie/Token/原始图片/完整响应正文，确认没有 schema 变化或未授权 Git 操作。最终报告区分已验证、未验证和因 Imgur 协议门槛跳过的内容。
