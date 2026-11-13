# 测试与验证

## 选择原则

验证以受影响行为和回归风险为依据。业务行为变更补充或更新对应测试，修复 Bug 优先添加能复现问题的回归测试；已有测试充分覆盖时说明依据，不为纯重命名、注释或实现细节机械新增测试。

先运行最小相关检查，便于快速定位问题；满足下表后，只有新增修改、失败或未消除的风险才扩大或重复验证。全量测试已经包含的局部测试不必为交付再次单独运行。

## 验证矩阵

多类改动取所需检查的并集。无法确定影响范围时，执行全量 JVM 测试和 Debug 构建。命令见[工作流](workflow.md)。

| 改动 | 必要验证 |
| --- | --- |
| 纯文档、Agent 指令、技能说明、注释 | diff、路径和链接、指令一致性；技能还检查触发边界与引用。不运行 Android 构建或业务测试 |
| 局部业务逻辑、ViewModel 状态转换 | 补充或更新行为测试，运行相关 JVM 测试和 `:app:assembleDebug` |
| 跨层行为、共享基础设施、协程/缓存策略，或影响范围不确定 | 相关回归测试、`:app:testDebugUnitTest`、`:app:assembleDebug` |
| 仅 Kotlin 重命名或无行为变化的重构 | 相关现有测试、`:app:assembleDebug`；跨层或共享契约变化按上一行处理 |
| UI、资源、Manifest、导航 | 相关现有测试、`:app:assembleDebug`，检查受影响的界面或系统行为；涉及权限、API 兼容性、资源限定符或可访问性时执行 `:app:lintDebug` |
| Gradle、依赖或构建配置 | `:app:testDebugUnitTest`、`:app:assembleDebug`；影响图床模块或共享依赖时加 `:image-hosting:test`；涉及 Lint 配置或 Android 兼容性时执行 `:app:lintDebug` |
| Room Entity/DAO/schema 或数据库打开/迁移策略 | 相关真实数据库测试、全量 JVM 测试、Debug 构建及 schema 检查；版本升级还需对应路径的 Migration 测试 |
| 网络、Cookie、登录、访问控制、HTML 解析 | 相关 MockWebServer/Parser 回归测试、全量 JVM 测试、Debug 构建 |
| Release、混淆、签名 | 相关检查及安全配置可用时的 Release 构建；运行受混淆影响的关键流程 |

## 测试实现

JVM 测试位于 `app/src/test/`，重点覆盖 UseCase、Repository、数据源、Parser、链接与格式化、ViewModel 状态转换、缓存策略和网络异常映射。网络测试使用 MockWebServer 或测试替身，不访问真实 V2EX；HTML fixture 最小化且脱敏。协程和 Flow 使用 `kotlinx-coroutines-test`，断言优先使用 Truth。

- **共享测试替身优先**：公共接口（如 `TestV2exRawApi`、`FakeAuthRepository`、`FakeUserRepository`、`FakeMemberTagRepository`、`FakeSettingsRepository`、`FakeNodeRepository`、`FakeStringKeyValueStorage`、`FakeTopicDao`）集中在 `app/src/test/java/app/mystery0/nodeflow/testing/`，新增测试优先复用或扩展，避免在各测试文件中重复实现接口。
- **`MockWebServer` 使用边界**：仅当测试真实依赖 OkHttp 拦截器、请求头/UA、表单或 Multipart 编码、HTTP 重定向最终 URL 校验或 HTTP 缓存语义时使用 `MockWebServer`；其余远端数据源和仓储测试优先继承或构造 `TestV2exRawApi`。
- **Robolectric 使用门槛**：Robolectric（含 Compose / Native Graphics）仅用于确需 Android 视图排版几何或 Coil Android 资源/缓存链路的行为回归（现有 `Base64LayoutRobolectricTest`、`CoilImageLoaderTest`、`RichImageRequestTest`）。纯业务逻辑、状态机、Parser、存储与网络映射不得新增 `@RunWith(RobolectricTestRunner::class)`。

图床模块 JVM 测试位于 `image-hosting/src/test/`，使用 `:image-hosting:test` 运行；覆盖契约、注册表及 V2EX/Imgur 协议替身，不访问真实图床。

需要真实 Room 的测试放入 `app/src/androidTest/`，不能用 Fake DAO 的 JVM 测试替代。`DatabaseMigrationPolicyTest` 使用生产 Koin 数据库模块和独立临时目录，覆盖版本 1 草稿重开保留、模拟版本 2→1 缺失迁移时拒绝打开且数据保留；不接触应用的真实 `nodeflow.db`。`ListCacheMergeTest` 额外覆盖列表刷新保留主题/节点详情及显式清除正文。
目前没有实际版本升级路径的 Migration 测试，也没有全量 DAO 行为覆盖；新增 schema/迁移时仍需补齐对应测试。

聚焦命令：`./gradlew :app:connectedDeviceTestAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.mystery0.nodeflow.core.database.DatabaseMigrationPolicyTest`。当前版本 1 基线及迁移规则见[存储说明](../subsystems/storage.md)。

## 富文本布局的 JVM 原生渲染回归

`Base64LayoutRobolectricTest` 使用测试专用 Robolectric 4.17、Android API 35 和 Native Graphics，
直接运行生产 Compose / HtmlText 组件。覆盖显式换行、自动折行、200% 字号、眼睛重复切换、
稳定块级模式、作者空行、48dp 触控范围及明文邮箱的独立点击；不会访问真实帖子或使用截图中的邮箱。

聚焦命令：`./gradlew :app:testDebugUnitTest --tests '*Base64LayoutRobolectricTest'`。
生成的 PNG 位于 `app/build/outputs/layout-screenshots/`，不纳入源码提交。
几何断言验证占位与文字不重叠，截图还需实际查看，不能仅以编译或断言通过代替视觉检查。
这些结果不等同于用户手机、OEM 字体、TalkBack 或 Release 真机验收。

## 图片加载验证

- `CoilImageLoaderTest` 为 Robolectric 测试，使用生产加载器配置与 MockWebServer，覆盖网络 SVG 解码、磁盘缓存以及服务关闭后的原文件保存。
- `RichContentInstrumentedTest` 的图片用例在设备上验证本地 PNG 尺寸与 GIF 动画类型；Windows Robolectric 的原生 `ImageDecoder` 路径存在限制，不用替身成功冒充 Android 解码通过。
- `RichImageRequestTest` 以拦截替身验证正文图片首次加载与失败重试保持原 URL，不访问真实图床。
- `ZoomableImageViewerTest` 使用本地生成图片，覆盖加载、分享/关闭回调和加载失败时禁用分享，不等于真实图床访问或系统分享目标验收。

## 真机或模拟器验证

### 保留日常安装与登录态

设备自动化固定使用 `deviceTest` 构建：目标包名为 `app.mystery0.nodeflow.uitest`，测试 APK 包名为 `app.mystery0.nodeflow.uitest.test`，应用名称为“NodeFlow 测试”。它使用 Debug 签名和独立的应用存储，不读取日常包的会话、Cookie 或数据库。`testBuildType` 已设为 `deviceTest`，默认 `connectedAndroidTest` 也只针对该测试构建；不要通过临时参数或 init script 把目标包名改回日常包。

```powershell
# 先构建并核对 APK 的 applicationId 与 instrumentation targetPackage
.\gradlew.bat :app:assembleDeviceTest :app:assembleDeviceTestAndroidTest
# 运行所需测试；可追加 android.testInstrumentationRunnerArguments.class 筛选
.\gradlew.bat :app:connectedDeviceTestAndroidTest
```

设备测试工具可能在失败后仍清理目标应用，因此运行前必须确认目标 APK 和 instrumentation 都指向上述测试包。只有“NodeFlow 测试”可被测试工具安装、卸载或重建；模拟器中用户日常使用的 `app.mystery0.nodeflow` 不得被卸载或执行 `pm clear`。不以“先卸载再安装”处理签名冲突，也不复制用户凭据到测试包。

需要在日常包体验新版时，先核对现有安装与待安装 APK 的签名、包名和版本，再用 `adb install -r` 同签名覆盖安装。签名不匹配或版本不兼容时停止安装，保留现有应用。覆盖安装通常保留本地数据，但登录是否持续有效仍受服务端会话和应用数据迁移逻辑影响。

### 验证范围

只验证本次受影响的流程，典型范围如下：

- UI：Dynamic Color、深色模式、edge-to-edge、系统栏和字体缩放。
- 导航：返回栈、外部深链和浏览器回退。
- 会话：登录页面、Cookie 持久化和受限页面处理。
- 内容：图片加载、SVG、正文图片与大图缩放。
- 恢复：网络切换、离线缓存和进程重启。

缺少设备、账户或环境时，完成可执行检查，并在交付中说明未验证流程、原因和剩余风险；不声称仅凭 JVM 测试确认了设备行为。
