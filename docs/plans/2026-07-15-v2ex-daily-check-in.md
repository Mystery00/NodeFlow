# V2EX 每日签到实施计划

> **执行要求：** 按测试驱动方式逐项实现；本计划由当前任务直接执行，不创建提交。

**目标：** 在“我的”页面通过现有 Retrofit/OkHttp 会话安全领取每日登录奖励，成功后刷新账号概览并显示 Toast。

**架构：** `AccountScreen → AccountViewModel → CheckInUseCase → AccountOverviewRepository → AccountRemoteDataSource → V2exRawApi`。RemoteDataSource 在领取前重新读取每日任务页并校验 `once`，领取后根据最终 URL 与 DOM 确认成功，再读取余额流水获取奖励。

**技术栈：** Kotlin、Coroutines/Flow、Retrofit/OkHttp、Jsoup、Jetpack Compose、Koin、JUnit/Truth。

## 全局约束

- 所有 V2EX 网络请求继续复用现有 Retrofit、OkHttp、CookieJar 和 User-Agent，不引入 WebView 或第二套会话。
- 今天不访问真实 V2EX 领取接口；网络行为只使用测试替身验证。
- `once` 只在单次调用内使用，不持久化、不写日志、不进入 UI 状态。
- 不能只凭 HTTP 2xx 判断成功；必须检查最终 URL、登录/受限/风控页面以及签到 DOM。
- 代码注释和文档使用中文，日志使用英文。

---

### 任务一：补充签到页面与余额流水解析

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`
- 测试：`app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`

- [x] 先添加失败测试，覆盖成功页正向结构、领取控件仍存在、风控文案和最新每日登录奖励正整数解析。
- [x] 运行 `:app:testDebugUnitTest --tests "*V2exHtmlParserTest"`，确认测试因新解析接口缺失而失败。
- [x] 实现最小解析接口并重新运行局部测试。

### 任务二：实现一次性 Retrofit/OkHttp 领取编排

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/core/network/V2exRawApi.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/data/account/AccountRemoteDataSource.kt`
- 测试：`app/src/test/java/app/mystery0/nodeflow/data/account/AccountRemoteDataSourceTest.kt`
- 修改：所有实现 `V2exRawApi` 的测试替身

- [x] 先添加失败测试，覆盖领取前已签到不发请求、缺失 `once` 不发请求、正确 Referer 与 `once`、重定向后成功、登录失效、错误最终 URL、风控页和成功后奖励缺失降级。
- [x] 运行 AccountRemoteDataSource 局部测试并确认失败原因符合预期。
- [x] 新增 `GET /mission/daily/redeem`，实现校验与领取结果模型，使局部测试通过。

### 任务三：接入 Repository、UseCase、ViewModel 与 Toast

**文件：**

- 修改：`app/src/main/java/app/mystery0/nodeflow/domain/account/AccountOverviewRepository.kt`
- 新增：`app/src/main/java/app/mystery0/nodeflow/domain/account/CheckInUseCase.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/data/account/AccountOverviewRepositoryImpl.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/domain/DomainModule.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/account/AccountUiEvent.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/account/AccountUiState.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/account/AccountViewModel.kt`
- 修改：`app/src/main/java/app/mystery0/nodeflow/feature/account/AccountScreen.kt`
- 测试：`app/src/test/java/app/mystery0/nodeflow/feature/account/AccountViewModelTest.kt`

- [x] 先添加失败测试，覆盖连点只调用一次、成功刷新状态与消息、奖励缺失时通用消息、失败消息以及消息消费。
- [x] 运行 ViewModel 局部测试并确认失败。
- [x] 接入 UseCase 与 DI，在待签到行显示按钮，请求期间禁用按钮，消费一次性消息并用 Android Toast 展示。
- [x] 重新运行局部测试并确认通过。

### 任务四：全量验证与文档同步

**文件：**

- 修改：`docs/index.md`
- 修改：`docs/investigations/2026-07-13-v2ex-daily-check-in.md`

- [x] 运行 `:app:testDebugUnitTest`。
- [x] 运行 `:app:assembleDebug`。
- [x] 运行 `:app:lintDebug`。
- [x] 在已启动模拟器上安装并打开 Debug 包，只检查页面布局、按钮状态和应用稳定性，不点击真实签到按钮。
- [x] 检查 Git diff，确认没有真实 Cookie、Token、用户名、`once` 或奖励样本值。
