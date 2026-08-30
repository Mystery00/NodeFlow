# V2EX 主题与回复感谢支持实施计划

> **For agentic workers:** 按 TDD 逐步实现；每项先添加并运行失败测试，再编写最小生产代码。

**Goal:** 在主题详情页支持主题感谢和回复感谢，并复用 V2EX 页面级 `once` 令牌。

**Architecture:** RemoteDataSource 调用 `POST /thank/topic/{topicId}?once=` 或 `POST /thank/reply/{replyId}?once=`，解析 JSON 后返回轮换令牌。Repository/UseCase 向 ViewModel 暴露操作，ViewModel 维护主题感谢与每条回复的感谢状态，Compose 负责展示与防重复点击。

**Tech Stack:** Kotlin、Jetpack Compose、Retrofit、Kotlin Serialization、Jsoup、JVM unit tests。

**Spec:** `docs/investigations/2026-08-30-v2ex-thank-topic-reply.md`

## Global Constraints

- 真实写操作仅限已完成的主题 `1238073` 主题感谢一次和第 20 楼回复感谢一次；实现与测试不得访问真实 V2EX。
- `once` 只保存在当前主题详情的内存状态，不写入日志、数据库、普通缓存或文档。
- 感谢接口使用 POST、空请求体、主题页 Referer，并按 JSON `success` 判断业务成功。
- 业务失败响应也必须更新轮换后的 `once`。
- UI 使用 Jetpack Compose，代码注释和项目文档使用中文，日志使用英文。

### Task 1: 解析感谢入口与令牌

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`

- [ ] 先为主题页面级 `thankOnce`、回复 `isThanked`/感谢可用状态写失败解析测试。
- [ ] 最小实现从主题感谢控件或页面脚本提取 `once`，从 `#thank_area_{replyId}` 识别已感谢状态。
- [ ] 运行解析器测试并保持全绿。

### Task 2: 网络与领域层

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/network/V2exRawApi.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSource.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/topic/TopicRepository.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/topic/ThankTopicUseCase.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/topic/ThankReplyUseCase.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImpl.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/DomainModule.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSourceTest.kt`

- [ ] 先添加失败测试验证两个 POST 的路径、once、Referer、空体和 JSON 成功/失败映射。
- [ ] 增加响应模型并实现最小调用链；不调用 `/ajax/money`。
- [ ] 业务失败抛出已有用户可见异常，同时返回/保留新 once。
- [ ] 运行网络和领域相关测试。

### Task 3: ViewModel 与 Compose UI

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailUiState.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailUiEvent.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/ui/ReplyItem.kt` if needed
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModelTest.kt`

- [ ] 先添加失败测试验证回复感谢成功、失败、令牌更新和重复提交保护。
- [ ] 增加主题/回复感谢事件与状态；请求期间禁用重复操作。
- [ ] 在主题操作区接入主题感谢；在回复更多菜单接入回复感谢并显示成功/错误状态。
- [ ] 保持未登录或未解析到 token 时不可操作。
- [ ] 运行 ViewModel 测试并构建。

### Task 4: 文档与完整验证

**Files:**
- Modify: `docs/subsystems/network-auth.md`
- Modify: `docs/subsystems/ui-navigation.md` if UI behavior is documented there
- Modify: `docs/index.md`

- [ ] 更新网络和 UI 专题文档及索引。
- [ ] 运行局部测试、`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintDebug`。
- [ ] 最终确认没有真实凭据、once 或未经授权写操作记录。
