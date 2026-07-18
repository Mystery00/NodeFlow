# V2EX 创建回复 Implementation Plan

> **面向 Agent 执行者：** REQUIRED SUB-SKILL：使用 `executing-plans` 逐任务实施；每个任务遵循 `test-driven-development`，按复选框跟踪进度并在批次检查点复核差异。

**目标：** 在主题详情中交付支持主题回复、指定楼层引用、V2EX 图库图片直链、持久化草稿和可靠提交确认的完整创建回复能力。

**架构：** 新增独立的 `reply` 领域与 `feature/replyeditor`，由回复编辑 ViewModel 协调写请求、图片上传、Room 草稿和登录会话；主题详情 ViewModel 仍只负责读取和提交成功后的刷新定位。写请求使用关闭连接自动重试的独立 Retrofit Client，HTML/JSON 结构识别集中在 Parser，外部写入不会被自动重复。

**技术栈：** Kotlin 2.4、Jetpack Compose、Material 3、Coroutines/Flow、Koin、Retrofit/OkHttp、Jsoup、kotlinx.serialization、Room 2.8.4、MockWebServer、AndroidX Test。

## 全局约束

- 仅支持 Android；最低 SDK 29，Compile/Target SDK 37，JVM target 21。
- UI 只使用 Jetpack Compose 和 Material 3，兼容 Dynamic Color、深色模式、edge-to-edge、IME 与字体缩放。
- 回复正文是纯文本；图片以独立一行的 HTTPS 原图直链插入，不生成 Markdown。
- 首版每次只选择一张 PNG、JPG、GIF 或 WebP，最大 6 MB，上传到当前登录账号的 V2EX 图库。
- 回复 POST 与图片上传 POST 均关闭 OkHttp 连接失败自动重试，不实现应用层自动重试。
- 网络测试只使用测试替身或 MockWebServer，不访问真实 V2EX。
- Room 从版本 3 升级到 4，提供 `Migration(3, 4)` 并导出 schema，禁止破坏性迁移。
- 代码注释、KDoc 和项目文档使用中文；如新增日志，日志文本必须使用英文且不得包含 Cookie、`once`、用户名、正文、完整图片 URL 或响应体。
- 保留用户未提交的无关修改；用户未要求 Git 提交，因此实施期间不提交、不推送。
- 真实图片上传会消耗铜币，真实回复会写入外部内容；自动化和模拟器布局验证不执行真实写入，真实回复前询问用户指定测试主题。

---

## 文件结构

### 新增文件

- `app/src/main/java/app/mystery0/nodeflow/core/database/entity/ReplyDraftEntity.kt`：Room 草稿与草稿图片 Entity、关系模型和领域映射。
- `app/src/main/java/app/mystery0/nodeflow/core/database/dao/ReplyDraftDao.kt`：按主题读取、写入、清除草稿与图片。
- `app/src/main/java/app/mystery0/nodeflow/core/network/V2exWriteApi.kt`：使用无自动重试 Client 的动态表单和 multipart 写接口。
- `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyModels.kt`：草稿、上传图片、提交结果和失败分类。
- `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyRepositories.kt`：回复提交、草稿和图片上传仓库接口。
- `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyUseCases.kt`：创建回复、上传图片和草稿读写用例。
- `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyRemoteDataSource.kt`：动态读取表单、单次提交和回读确认。
- `app/src/main/java/app/mystery0/nodeflow/data/reply/V2exImageRemoteDataSource.kt`：图库权限预检和单次 multipart 上传。
- `app/src/main/java/app/mystery0/nodeflow/data/reply/AndroidImageContentReader.kt`：从 `content://` 安全读取名称、MIME、大小和有限字节流。
- `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyDraftLocalDataSource.kt`：封装 DAO 与领域映射。
- `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyRepositoryImpl.kt`：回复提交 IO 调度与异常归类。
- `app/src/main/java/app/mystery0/nodeflow/data/reply/ImageUploadRepositoryImpl.kt`：本地图片校验、远端上传和异常归类。
- `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyDraftRepositoryImpl.kt`：草稿持久化实现。
- `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorUiState.kt`：编辑器状态、事件与一次性 Effect。
- `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorText.kt`：楼层引用和图片 URL 的纯文本插入规则。
- `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorViewModel.kt`：草稿防抖、登录、上传、提交状态机。
- `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorBottomSheet.kt`：允许背景交互、最高半屏的持久式编辑 Sheet。
- `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/ReplyFabVisibility.kt`：列表位置到 FAB 显隐的纯函数。
- `app/src/test/java/app/mystery0/nodeflow/data/reply/ReplyRemoteDataSourceTest.kt`：回复协议和成功确认测试。
- `app/src/test/java/app/mystery0/nodeflow/data/reply/V2exImageRemoteDataSourceTest.kt`：图库预检、multipart 与响应测试。
- `app/src/test/java/app/mystery0/nodeflow/data/reply/ImageUploadRepositoryImplTest.kt`：图片校验与失败映射测试。
- `app/src/test/java/app/mystery0/nodeflow/data/reply/ReplyDraftRepositoryImplTest.kt`：草稿仓库委托和映射测试。
- `app/src/test/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorTextTest.kt`：光标插入和去重测试。
- `app/src/test/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorViewModelTest.kt`：恢复、保存、上传、提交和登录状态测试。
- `app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/ReplyFabVisibilityTest.kt`：滚动方向显隐测试。
- `app/src/androidTest/java/app/mystery0/nodeflow/core/database/ReplyDraftMigrationTest.kt`：3→4 Migration 和 DAO 行为测试。

### 修改文件

- `gradle/libs.versions.toml`、`app/build.gradle.kts`：增加 Room Migration instrumentation 测试依赖与 Runner。
- `app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`：解析回复表单、服务端问题和图库页面。
- `app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`：新增对应脱敏最小 HTML/JSON 用例。
- `app/src/main/java/app/mystery0/nodeflow/core/database/NodeFlowDatabase.kt`、`DatabaseModule.kt`：注册 Entity、DAO 和 Migration 3→4。
- `app/src/main/java/app/mystery0/nodeflow/core/network/NetworkModule.kt`：注册 `V2exWriteApi` 的无重试 Retrofit。
- `app/src/main/java/app/mystery0/nodeflow/data/DataSourceModule.kt`、`RepositoryModule.kt`：注册回复数据源和三个仓库。
- `app/src/main/java/app/mystery0/nodeflow/domain/DomainModule.kt`：注册回复相关用例。
- `app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt`：以 `ReplyEditorViewModel` 替换占位 `EditorViewModel`。
- `app/src/main/java/app/mystery0/nodeflow/core/ui/ReplyItem.kt`：增加三点和直接回复操作。
- `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailUiState.kt`、`TopicDetailUiEvent.kt`、`TopicDetailViewModel.kt`：提交成功后刷新并暴露一次性楼层定位请求。
- `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`：整合 FAB、回复操作 Sheet 和持久回复 Sheet。
- `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowDestinations.kt`、`NodeFlowNavHost.kt`：移除无用 Editor 路由，注入回复 ViewModel 并处理登录、图库和成功 Effect。
- `docs/index.md`：把设计条目补成“设计 / 实施计划”链接。

### 删除文件

- `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorScreen.kt`
- `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorUiEvent.kt`
- `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorUiState.kt`
- `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorViewModel.kt`

---

### Task 1：锁定解析与领域契约

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyModels.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyRepositories.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`

**Interfaces:**
- Produces: `ParsedReplyForm`、`ParsedImageUploadPage`、`ParsedImageUploadResponse`、`ReplyConstraints`、`ReplyDraft`、`UploadedReplyImage`、`CreateReplyResult`、`ImageUploadResult` 和三个 Repository 接口。
- Consumes: 现有 `V2exHtmlParser.parseTopicHtml()`、`hasSignInEntry()`、`hasAccessChallenge()`。

- [ ] **Step 1：先写失败的 Parser 测试**

覆盖动态 action、正文参数、隐藏字段、`maxlength` 兜底边界、登录页拒绝、服务端 problem、图库上传页/无权限页和字符串 `success`：

```kotlin
@Test
fun parseReplyForm_readsDynamicFields() {
    val parsed = parser.parseReplyForm(
        topicId = 42,
        html = """
            <form method="post" action="/t/42">
              <textarea id="reply_content" name="content" maxlength="10000"></textarea>
              <input type="hidden" name="once" value="redacted" />
              <input type="hidden" name="return_to_page" value="3" />
            </form>
        """.trimIndent(),
    )
    assertThat(parsed?.actionUrl).isEqualTo("https://www.v2ex.com/t/42")
    assertThat(parsed?.contentField).isEqualTo("content")
    assertThat(parsed?.maxLength).isEqualTo(10_000)
    assertThat(parsed?.hiddenFields).containsExactly("once", "redacted", "return_to_page", "3")
}

@Test
fun parseImageUploadResponse_normalizesProtocolRelativeOriginalUrl() {
    val parsed = parser.parseImageUploadResponse(
        """{"success":"true","name":"abc","uri":"abc.png","url_o":"//i.v2ex.co/abc.png","url_b":"//i.v2ex.co/abcb.png"}""",
    )
    assertThat(parsed?.originalUrl).isEqualTo("https://i.v2ex.co/abc.png")
}
```

- [ ] **Step 2：运行 Parser 局部测试，确认失败原因是接口不存在**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest"`

Expected: `FAILED`，编译错误指向 `parseReplyForm` 或 `parseImageUploadResponse` 未定义。

- [ ] **Step 3：新增明确的领域模型和仓库接口**

```kotlin
data class ReplyDraft(
    val topicId: Long,
    val content: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val images: List<UploadedReplyImage>,
    val updatedAtEpochMillis: Long,
)

data class ReplyConstraints(val maxLength: Int)

data class UploadedReplyImage(
    val imageId: String,
    val originalUrl: String,
    val detailUrl: String,
    val originalFileName: String,
    val createdAtEpochMillis: Long,
)

sealed interface CreateReplyResult {
    data class Success(val floor: Int) : CreateReplyResult
    data class Failure(val reason: ReplyFailureReason, val message: String) : CreateReplyResult
}

enum class ReplyFailureReason {
    AuthenticationRequired, TopicUnavailable, AntiFlood, SubmitUnconfirmed, Network, Server, Parse,
}

sealed interface ImageUploadResult {
    data class Success(val image: UploadedReplyImage) : ImageUploadResult
    data class Failure(val reason: ImageUploadFailureReason, val message: String) : ImageUploadResult
}

enum class ImageUploadFailureReason {
    AuthenticationRequired, PermissionDenied, QuotaExceeded, UnsupportedType,
    FileTooLarge, UnreadableFile, UploadUnconfirmed, Network, Server,
}

interface ReplyRepository {
    suspend fun loadConstraints(topicId: Long): Result<ReplyConstraints>
    suspend fun createReply(topicId: Long, content: String, currentUsername: String): CreateReplyResult
}

interface ReplyDraftRepository {
    suspend fun load(topicId: Long): ReplyDraft?
    suspend fun save(draft: ReplyDraft)
    suspend fun addImage(topicId: Long, image: UploadedReplyImage)
    suspend fun clear(topicId: Long)
}

interface ImageUploadRepository {
    suspend fun upload(contentUri: String): ImageUploadResult
}
```

- [ ] **Step 4：实现严格 Parser**

`parseReplyForm()` 只接受目标主题的 POST 表单；保留全部命名隐藏字段但不记录值。`parseImageUploadPage()` 返回 `Available`、`AuthenticationRequired` 或 `PermissionDenied`；`parseImageUploadResponse()` 同时兼容字符串/Boolean 成功值：

```kotlin
data class ParsedReplyForm(
    val actionUrl: String,
    val contentField: String,
    val maxLength: Int,
    val hiddenFields: Map<String, String>,
)

fun parseReplyForm(topicId: Long, html: String): ParsedReplyForm? {
    val document = Jsoup.parse(html, V2EX_BASE_URL)
    if (document.hasRestrictedSignInForm() || hasAccessChallenge(html)) return null
    val form = document.select("form[method=post]").firstOrNull { form ->
        val action = form.absUrl("action")
        form.selectFirst("textarea[name]") != null &&
            action == "$V2EX_BASE_URL/t/$topicId"
    } ?: return null
    val textarea = form.selectFirst("textarea[name]") ?: return null
    return ParsedReplyForm(
        actionUrl = form.absUrl("action"),
        contentField = textarea.attr("name"),
        maxLength = textarea.attr("maxlength").toIntOrNull()?.takeIf { it > 0 } ?: 10_000,
        hiddenFields = form.select("input[type=hidden][name]").associate {
            it.attr("name") to it.attr("value")
        },
    )
}
```

- [ ] **Step 5：运行 Parser 测试并确认通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest"`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 6：检查本任务差异**

Run: `git diff --check`

Expected: 无输出；确认测试 HTML 和断言不含真实用户名、Cookie、`once` 或图片 ID。

---

### Task 2：实现不自动重试的写网络与回读确认

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/network/V2exWriteApi.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyRemoteDataSource.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/V2exImageRemoteDataSource.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/network/NetworkModule.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/reply/ReplyRemoteDataSourceTest.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/reply/V2exImageRemoteDataSourceTest.kt`

**Interfaces:**
- Consumes: Task 1 的 Parser 返回类型与 `CreateReplyResult`、`ImageUploadResult`。
- Produces: `ReplyRemoteDataSource.createReply(topicId, content, currentUsername)` 和 `V2exImageRemoteDataSource.upload(payload)`。

- [ ] **Step 1：写失败的 MockWebServer 协议测试**

回复测试按顺序断言“GET 最新表单 → 读取提交前末页 → 单次 POST → GET 提交后末页”，并验证动态字段、Origin、Referer 与无第二次 POST；图片测试断言权限 GET、multipart 名称 `qqfile`、`X-Requested-With` 和单次 POST：

```kotlin
assertThat(server.takeRequest().method).isEqualTo("GET")
assertThat(server.takeRequest().path).isEqualTo("/t/42?p=2")
val post = server.takeRequest()
assertThat(post.method).isEqualTo("POST")
val postBody = post.body.readUtf8()
assertThat(postBody).contains("once=redacted")
assertThat(postBody).contains("content=hello")
assertThat(post.getHeader("Origin")).isEqualTo(server.url("/").toString().trimEnd('/'))
assertThat(server.requestCount).isEqualTo(4)
```

- [ ] **Step 2：运行两个远端数据源测试，确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.reply.*RemoteDataSourceTest"`

Expected: `FAILED`，目标类和 `V2exWriteApi` 尚不存在。

- [ ] **Step 3：定义独立写 API 并注册无重试 Client**

```kotlin
interface V2exWriteApi {
    @GET
    suspend fun getHtml(@Url url: String): Response<ResponseBody>

    @FormUrlEncoded
    @POST
    suspend fun submitForm(
        @Url url: String,
        @FieldMap fields: Map<String, String>,
        @Header("Origin") origin: String,
        @Header("Referer") referer: String,
    ): Response<ResponseBody>

    @Multipart
    @POST("i/upload")
    suspend fun uploadImage(
        @Part file: MultipartBody.Part,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Requested-With") requestedWith: String = "XMLHttpRequest",
        @Header("Referer") referer: String,
    ): Response<ResponseBody>
}
```

```kotlin
single(named("v2exWriteClient")) {
    get<OkHttpClient>().newBuilder()
        .retryOnConnectionFailure(false)
        .build()
}
single {
    Retrofit.Builder()
        .baseUrl("https://www.v2ex.com/")
        .client(get(named("v2exWriteClient")))
        .build()
        .create(V2exWriteApi::class.java)
}
```

- [ ] **Step 4：实现回复远端状态机**

实现必须以可注入的 `baseUrl`（生产默认 `https://www.v2ex.com/`，测试传入 MockWebServer URL）验证 HTTPS V2EX 同源 action、最终 URL、登录/受限/Cloudflare 页面和服务端 `.problem`。`loadConstraints()` 读取最新表单并返回动态 `maxlength`；提交前后末页通过“当前用户名 + 标准化正文 + 新回复 ID”确认，未命中返回 `SubmitUnconfirmed`：

```kotlin
suspend fun createReply(
    topicId: Long,
    content: String,
    currentUsername: String,
): CreateReplyResult = safeNetworkCall {
    val topicUrl = "$baseUrl/t/$topicId"
    val formHtml = api.getHtml(topicUrl).guardTopicResponse(topicId)
    val form = parser.parseReplyForm(topicId, formHtml)
        ?: return@safeNetworkCall failure(ReplyFailureReason.TopicUnavailable, "当前主题无法回复")
    val before = loadLastPage(topicId, formHtml)
    val fields = form.hiddenFields + (form.contentField to content)
    val response = api.submitForm(form.actionUrl, fields, baseOrigin, topicUrl)
    val responseHtml = response.guardReplySubmitResponse(topicId)
    parser.parseV2exProblem(responseHtml)?.let { return@safeNetworkCall classifyProblem(it) }
    val after = loadLastPage(topicId, responseHtml)
    val previousIds = before.replies.mapTo(mutableSetOf()) { it.id }
    val match = after.replies.lastOrNull { reply ->
        reply.id !in previousIds &&
            reply.author.username.equals(currentUsername, ignoreCase = true) &&
            normalizeReplyContent(reply.content) == normalizeReplyContent(content)
    }
    match?.let { CreateReplyResult.Success(it.floor) }
        ?: failure(ReplyFailureReason.SubmitUnconfirmed, "回复结果无法确认，请先刷新主题后再决定是否重试")
}
```

- [ ] **Step 5：实现图库权限预检与 multipart 上传**

`ImageUploadPayload` 只在数据层持有有限字节；上传响应必须同时满足 HTTP 成功、合法 JSON、成功标记和 HTTPS `i.v2ex.co` 原图地址：

```kotlin
data class ImageUploadPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

suspend fun upload(payload: ImageUploadPayload): ImageUploadResult = safeNetworkCall {
    when (parser.parseImageUploadPage(api.getHtml("${baseUrl}i/upload").guardGalleryPage())) {
        ParsedImageUploadPage.Available -> Unit
        ParsedImageUploadPage.AuthenticationRequired -> return@safeNetworkCall authFailure()
        ParsedImageUploadPage.PermissionDenied -> return@safeNetworkCall permissionFailure()
    }
    val requestBody = payload.bytes.toRequestBody(payload.mimeType.toMediaType())
    val part = MultipartBody.Part.createFormData("qqfile", payload.fileName, requestBody)
    val response = api.uploadImage(part, referer = "${baseUrl}i/upload")
    val parsed = parser.parseImageUploadResponse(response.bodyStringOrThrow())
        ?: return@safeNetworkCall unconfirmedFailure()
    ImageUploadResult.Success(parsed.toDomain(payload.fileName, clock()))
}
```

- [ ] **Step 6：运行远端数据源测试并确认通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.reply.*RemoteDataSourceTest"`

Expected: `BUILD SUCCESSFUL`；MockWebServer 的 POST 数量在所有失败路径都不超过 1。

- [ ] **Step 7：检查敏感信息和自动重试设置**

Run: `rg -n "retryOnConnectionFailure|once|cookie|responseHtml|content" app/src/main/java/app/mystery0/nodeflow/core/network app/src/main/java/app/mystery0/nodeflow/data/reply`

Expected: 写 Client 明确为 `false`；业务代码不打印命中的敏感字段或响应体。

---

### Task 3：实现 Room 草稿与 3→4 Migration

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/database/entity/ReplyDraftEntity.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/database/dao/ReplyDraftDao.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyDraftLocalDataSource.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/database/NodeFlowDatabase.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/database/DatabaseModule.kt`
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Test: `app/src/androidTest/java/app/mystery0/nodeflow/core/database/ReplyDraftMigrationTest.kt`
- Generated: `app/schemas/app.mystery0.nodeflow.core.database.NodeFlowDatabase/4.json`

**Interfaces:**
- Consumes: Task 1 的 `ReplyDraft` 和 `UploadedReplyImage`。
- Produces: `ReplyDraftDao`、`ReplyDraftLocalDataSource`、可测试的 `MIGRATION_3_4`。

- [ ] **Step 1：增加 instrumentation 测试配置并写失败的 Migration 测试**

在版本目录增加 `androidx-test-runner`、`androidx-test-ext-junit`、`androidx-room-testing`；设置：

```kotlin
defaultConfig {
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
}

androidTestImplementation(libs.androidx.test.ext.junit)
androidTestImplementation(libs.androidx.test.runner)
androidTestImplementation(libs.androidx.room.testing)
```

测试先创建版本 3 数据库，插入现有 topic，执行迁移后验证原数据仍在、新表可写、删除草稿级联删除图片：

```kotlin
helper.createDatabase(TEST_DB, 3).apply {
    execSQL("""
        INSERT INTO topics(
            id, title, url, nodeName, nodeTitle, authorName,
            replyCount, cachedAtEpochMillis
        ) VALUES(
            42, '保留主题', 'https://www.v2ex.com/t/42', 'sandbox',
            'Sandbox', 'tester', 0, 1
        )
    """.trimIndent())
    close()
}
val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
db.execSQL("INSERT INTO reply_drafts(topicId, content, selectionStart, selectionEnd, updatedAtEpochMillis) VALUES(42, 'draft', 5, 5, 1)")
db.execSQL("INSERT INTO reply_draft_images(imageId, topicId, originalUrl, detailUrl, originalFileName, createdAtEpochMillis) VALUES('img', 42, 'https://i.v2ex.co/img.png', 'https://www.v2ex.com/i/img.png', 'img.png', 1)")
db.execSQL("DELETE FROM reply_drafts WHERE topicId = 42")
assertThat(queryCount(db, "reply_draft_images")).isEqualTo(0)
```

- [ ] **Step 2：运行 Migration 测试编译，确认 Entity/Migration 尚缺失**

Run: `./gradlew.bat :app:compileDebugAndroidTestKotlin`

Expected: `FAILED`，缺少 `MIGRATION_3_4` 或数据库版本 4 定义。

- [ ] **Step 3：实现 Entity、关系和 DAO**

```kotlin
@Entity(tableName = "reply_drafts")
data class ReplyDraftEntity(
    @PrimaryKey val topicId: Long,
    val content: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val updatedAtEpochMillis: Long,
)

@Entity(
    tableName = "reply_draft_images",
    foreignKeys = [ForeignKey(
        entity = ReplyDraftEntity::class,
        parentColumns = ["topicId"],
        childColumns = ["topicId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("topicId")],
)
data class ReplyDraftImageEntity(
    @PrimaryKey val imageId: String,
    val topicId: Long,
    val originalUrl: String,
    val detailUrl: String,
    val originalFileName: String,
    val createdAtEpochMillis: Long,
)

@Dao
interface ReplyDraftDao {
    @Transaction
    @Query("SELECT * FROM reply_drafts WHERE topicId = :topicId")
    suspend fun draft(topicId: Long): ReplyDraftWithImages?

    @Upsert suspend fun upsertDraft(draft: ReplyDraftEntity)
    @Upsert suspend fun upsertImage(image: ReplyDraftImageEntity)
    @Query("DELETE FROM reply_drafts WHERE topicId = :topicId")
    suspend fun delete(topicId: Long)
}
```

- [ ] **Step 4：升级数据库并实现精确 Migration**

```kotlin
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reply_drafts (
                topicId INTEGER NOT NULL,
                content TEXT NOT NULL,
                selectionStart INTEGER NOT NULL,
                selectionEnd INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(topicId)
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS reply_draft_images (
                imageId TEXT NOT NULL,
                topicId INTEGER NOT NULL,
                originalUrl TEXT NOT NULL,
                detailUrl TEXT NOT NULL,
                originalFileName TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(imageId),
                FOREIGN KEY(topicId) REFERENCES reply_drafts(topicId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS index_reply_draft_images_topicId ON reply_draft_images(topicId)")
    }
}
```

把数据库版本改为 4，注册两个 Entity、`replyDraftDao()`、Koin DAO 和 `MIGRATION_3_4`。同时把导出的 schema 提供给 instrumentation 测试：

```kotlin
sourceSets {
    getByName("androidTest").assets.srcDir("$projectDir/schemas")
}
```

- [ ] **Step 5：实现 LocalDataSource 映射并生成 schema**

```kotlin
class ReplyDraftLocalDataSource(private val dao: ReplyDraftDao) {
    suspend fun load(topicId: Long): ReplyDraft? = dao.draft(topicId)?.toDomain()
    suspend fun save(draft: ReplyDraft) = dao.upsertDraft(draft.toEntity())
    suspend fun addImage(topicId: Long, image: UploadedReplyImage) =
        dao.upsertImage(image.toEntity(topicId))
    suspend fun clear(topicId: Long) = dao.delete(topicId)
}
```

Run: `./gradlew.bat :app:kspDebugKotlin`

Expected: `BUILD SUCCESSFUL`，生成版本 4 schema。

- [ ] **Step 6：在已启动模拟器运行 Migration 测试**

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.mystery0.nodeflow.core.database.ReplyDraftMigrationTest`

Expected: `BUILD SUCCESSFUL`，Migration、旧数据保留和级联删除均通过。

- [ ] **Step 7：核对 schema 和差异**

Run: `git diff --check; git diff -- app/schemas/app.mystery0.nodeflow.core.database.NodeFlowDatabase/4.json`

Expected: 无空白错误；schema 包含两张新表、外键和 `topicId` 索引。

---

### Task 4：实现图片读取、三个仓库和领域用例

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/AndroidImageContentReader.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyRepositoryImpl.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/ImageUploadRepositoryImpl.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/reply/ReplyDraftRepositoryImpl.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/reply/ReplyUseCases.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/DataSourceModule.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/RepositoryModule.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/DomainModule.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/reply/ImageUploadRepositoryImplTest.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/reply/ReplyDraftRepositoryImplTest.kt`

**Interfaces:**
- Consumes: Tasks 1–3 的仓库接口、远端数据源和 LocalDataSource。
- Produces: `GetReplyConstraintsUseCase`、`CreateReplyUseCase`、`UploadImageUseCase`、`LoadReplyDraftUseCase`、`SaveReplyDraftUseCase`、`AddReplyDraftImageUseCase`、`ClearReplyDraftUseCase`。

- [ ] **Step 1：写失败的图片校验和草稿委托测试**

逐项覆盖 6 MB+1、未知 MIME、缺失扩展名时按 MIME 补全、安全读取失败、成功上传、草稿保存和远端图片只做本地记录：

```kotlin
@Test
fun upload_rejectsFileLargerThanSixMegabytesWithoutRemoteRequest() = runTest {
    reader.result = ImageContent(
        fileName = "large.png",
        mimeType = "image/png",
        bytes = ByteArray(6 * 1024 * 1024 + 1),
    )
    val result = repository.upload("content://test/large")
    assertThat(result).isEqualTo(
        ImageUploadResult.Failure(ImageUploadFailureReason.FileTooLarge, "图片不能超过 6 MB"),
    )
    assertThat(remote.requests).isEmpty()
}
```

- [ ] **Step 2：运行仓库局部测试，确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.reply.*RepositoryImplTest"`

Expected: `FAILED`，仓库实现和图片读取类型尚不存在。

- [ ] **Step 3：实现有限读取与文件规则**

```kotlin
private const val MAX_IMAGE_BYTES = 6 * 1024 * 1024

fun read(contentUri: String): ImageContent {
    val uri = Uri.parse(contentUri)
    require(uri.scheme == ContentResolver.SCHEME_CONTENT)
    val mime = resolver.getType(uri)?.lowercase()
        ?: throw ImageReadException.UnsupportedType
    val extension = ALLOWED_MIME_TYPES[mime]
        ?: throw ImageReadException.UnsupportedType
    val displayName = queryDisplayName(uri)
        ?.takeIf { name -> name.substringAfterLast('.', "").lowercase() in ALLOWED_EXTENSIONS }
        ?: "upload.$extension"
    val bytes = resolver.openInputStream(uri)?.use { stream ->
        stream.readNBytes(MAX_IMAGE_BYTES + 1)
    } ?: throw ImageReadException.Unreadable
    if (bytes.size > MAX_IMAGE_BYTES) throw ImageReadException.TooLarge
    return ImageContent(displayName, mime, bytes)
}
```

- [ ] **Step 4：实现仓库异常映射与 IO 调度**

```kotlin
class ImageUploadRepositoryImpl(
    private val reader: AndroidImageContentReader,
    private val remote: V2exImageRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageUploadRepository {
    override suspend fun upload(contentUri: String): ImageUploadResult = withContext(ioDispatcher) {
        val content = try {
            reader.read(contentUri)
        } catch (_: ImageReadException.UnsupportedType) {
            return@withContext ImageUploadResult.Failure(
                ImageUploadFailureReason.UnsupportedType,
                "仅支持 PNG、JPG、GIF 或 WebP 图片",
            )
        } catch (_: ImageReadException.TooLarge) {
            return@withContext ImageUploadResult.Failure(
                ImageUploadFailureReason.FileTooLarge,
                "图片不能超过 6 MB",
            )
        } catch (_: Throwable) {
            return@withContext ImageUploadResult.Failure(
                ImageUploadFailureReason.UnreadableFile,
                "无法读取所选图片",
            )
        }
        remote.upload(content.toPayload())
    }
}
```

回复仓库只调度 `ReplyRemoteDataSource`，草稿仓库只调度 LocalDataSource；网络/解析异常映射为领域失败并保留原始草稿。

- [ ] **Step 5：实现薄 UseCase 并注册 Koin**

```kotlin
class CreateReplyUseCase(private val repository: ReplyRepository) {
    suspend operator fun invoke(topicId: Long, content: String, username: String) =
        repository.createReply(topicId, content, username)
}

class GetReplyConstraintsUseCase(private val repository: ReplyRepository) {
    suspend operator fun invoke(topicId: Long) = repository.loadConstraints(topicId)
}

class UploadImageUseCase(private val repository: ImageUploadRepository) {
    suspend operator fun invoke(contentUri: String) = repository.upload(contentUri)
}

class SaveReplyDraftUseCase(private val repository: ReplyDraftRepository) {
    suspend operator fun invoke(draft: ReplyDraft) = repository.save(draft)
}
```

DataSource Module 注册 `ReplyRemoteDataSource`、`V2exImageRemoteDataSource`、`ReplyDraftLocalDataSource` 和 `AndroidImageContentReader(androidContext().contentResolver)`；Repository/Domain Module 注册所有接口与用例。

- [ ] **Step 6：运行仓库测试并确认通过**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.reply.*RepositoryImplTest"`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 7：运行 Koin 相关编译检查**

Run: `./gradlew.bat :app:compileDebugKotlin`

Expected: `BUILD SUCCESSFUL`，没有未注册构造参数或 Android 类型泄漏到 domain 包。

---

### Task 5：实现回复文本规则和 ViewModel 状态机

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorUiState.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorText.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorTextTest.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorViewModelTest.kt`

**Interfaces:**
- Consumes: Task 4 的用例和现有 `ObserveAuthSessionUseCase`。
- Produces: `ReplyEditorUiState`、`ReplyEditorUiEvent`、`ReplyEditorEffect` 和 `ReplyEditorViewModel.effects`。

- [ ] **Step 1：先写失败的纯函数测试**

覆盖起始/中间/末尾插入、选区替换、同一引用紧邻光标去重、图片独立行和选区范围钳制：

```kotlin
@Test
fun insertFloorReference_atCurrentSelection() {
    val result = insertFloorReference(
        value = TextFieldValue("前后", selection = TextRange(1)),
        username = "alice",
        floor = 7,
    )
    assertThat(result.text).isEqualTo("前@alice #7 后")
    assertThat(result.selection.start).isEqualTo("前@alice #7 ".length)
}

@Test
fun insertImageUrl_putsBareUrlOnItsOwnLine() {
    val result = insertImageUrl(
        TextFieldValue("前后", selection = TextRange(1)),
        "https://i.v2ex.co/image.png",
    )
    assertThat(result.text).isEqualTo("前\nhttps://i.v2ex.co/image.png\n后")
}
```

- [ ] **Step 2：运行文本测试，确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.replyeditor.ReplyEditorTextTest"`

Expected: `FAILED`，插入函数尚不存在。

- [ ] **Step 3：实现无副作用插入函数**

```kotlin
private fun TextRange.clamped(textLength: Int): TextRange = TextRange(
    start.coerceIn(0, textLength),
    end.coerceIn(0, textLength),
)

fun insertFloorReference(
    value: TextFieldValue,
    username: String,
    floor: Int,
): TextFieldValue {
    val selection = value.selection.clamped(value.text.length)
    val token = "@${username.trim()} #$floor "
    if (selection.collapsed && value.text.substring(0, selection.start).endsWith(token)) return value
    return replaceSelection(value, selection, token)
}

fun insertImageUrl(value: TextFieldValue, url: String): TextFieldValue {
    val selection = value.selection.clamped(value.text.length)
    val prefix = if (selection.start > 0 && value.text[selection.start - 1] != '\n') "\n" else ""
    val suffix = if (selection.end < value.text.length && value.text[selection.end] != '\n') "\n" else ""
    return replaceSelection(value, selection, "$prefix$url$suffix")
}
```

- [ ] **Step 4：写失败的 ViewModel 状态测试**

覆盖草稿恢复、400 ms 防抖写入、关闭立即补写、登录态、上传期间锁定、上传成功插入并记录、失败保留、空正文、过长正文、提交成功清草稿并发 Effect、无法确认保留：

```kotlin
viewModel.onEvent(ReplyEditorUiEvent.OpenFloorReply("alice", 7))
viewModel.onEvent(ReplyEditorUiEvent.ContentChanged(TextFieldValue("hello")))
advanceTimeBy(399)
assertThat(drafts.saved).isEmpty()
advanceTimeBy(1)
runCurrent()
assertThat(drafts.saved.single().content).isEqualTo("hello")
```

- [ ] **Step 5：实现状态、事件和一次性 Effect**

```kotlin
data class ReplyEditorUiState(
    val isOpen: Boolean = false,
    val value: TextFieldValue = TextFieldValue(),
    val images: List<UploadedReplyImage> = emptyList(),
    val isLoggedIn: Boolean = false,
    val isUploading: Boolean = false,
    val isSubmitting: Boolean = false,
    val maxLength: Int = 10_000,
    val message: String? = null,
    val showGalleryAction: Boolean = false,
    val showClearConfirmation: Boolean = false,
)

sealed interface ReplyEditorUiEvent {
    data object OpenTopicReply : ReplyEditorUiEvent
    data class OpenFloorReply(val username: String, val floor: Int) : ReplyEditorUiEvent
    data class ContentChanged(val value: TextFieldValue) : ReplyEditorUiEvent
    data class ImageSelected(val contentUri: String) : ReplyEditorUiEvent
    data object Submit : ReplyEditorUiEvent
    data object Close : ReplyEditorUiEvent
    data object FlushDraft : ReplyEditorUiEvent
    data object ClearRequested : ReplyEditorUiEvent
    data object ClearConfirmed : ReplyEditorUiEvent
    data object MessageConsumed : ReplyEditorUiEvent
    data object GalleryRequested : ReplyEditorUiEvent
}

sealed interface ReplyEditorEffect {
    data object RequestLogin : ReplyEditorEffect
    data object OpenGallery : ReplyEditorEffect
    data class ReplyCreated(val floor: Int) : ReplyEditorEffect
}
```

- [ ] **Step 6：实现 ViewModel 状态机**

构造函数注入 topicId、七个回复用例和登录 Flow。初始化并在打开编辑器时调用 `GetReplyConstraintsUseCase`，成功则更新 `maxLength`，读取失败继续使用 10,000 兜底且不覆盖草稿。上传前立即补写正文，上传成功先插入 URL、保存草稿，再写入图片记录，确保外键父行已存在。上传锁定编辑与发布；提交取当前用户名；成功清空并关闭，失败不改正文：

```kotlin
private fun submit() {
    val current = uiState.value
    val content = current.value.text
    if (content.isBlank()) return showMessage("回复内容不能为空")
    if (content.length > current.maxLength) return showMessage("回复内容不能超过 ${current.maxLength} 个字符")
    val username = currentUsername ?: return emitEffect(ReplyEditorEffect.RequestLogin)
    if (current.isUploading || current.isSubmitting) return
    viewModelScope.launch {
        flushDraftNow()
        _uiState.update { it.copy(isSubmitting = true, message = null) }
        when (val result = createReply(topicId, content, username)) {
            is CreateReplyResult.Success -> {
                clearDraft(topicId)
                _uiState.update { ReplyEditorUiState(isLoggedIn = true) }
                _effects.send(ReplyEditorEffect.ReplyCreated(result.floor))
            }
            is CreateReplyResult.Failure -> _uiState.update {
                it.copy(
                    isSubmitting = false,
                    message = result.message,
                    showGalleryAction = false,
                )
            }
        }
    }
}
```

- [ ] **Step 7：替换 FeatureModule 注册并运行测试**

```kotlin
viewModel {
    ReplyEditorViewModel(
        savedStateHandle = get(),
        createReply = get(),
        getReplyConstraints = get(),
        uploadImage = get(),
        loadDraft = get(),
        saveDraft = get(),
        addDraftImage = get(),
        clearDraft = get(),
        observeAuthSession = get(),
    )
}
```

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.replyeditor.*Test"`

Expected: `BUILD SUCCESSFUL`。

---

### Task 6：实现 Bottom Sheet、回复项操作和 FAB 显隐

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorBottomSheet.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/ReplyFabVisibility.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/ui/ReplyItem.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/ReplyFabVisibilityTest.kt`
- Existing tests: `app/src/test/java/app/mystery0/nodeflow/core/ui/ReplyItemAuthorTest.kt`
- Existing tests: `app/src/test/java/app/mystery0/nodeflow/core/ui/ReplyItemLinkTest.kt`

**Interfaces:**
- Consumes: Task 5 的 UiState/UiEvent。
- Produces: `ReplyEditorBottomSheet`、回复项 `onMoreClick/onReplyClick` 和 `replyFabVisibleAfterScroll()`。

- [ ] **Step 1：写失败的滚动显隐测试**

```kotlin
@Test fun scrollingDown_hidesFab() {
    assertThat(replyFabVisibleAfterScroll(ScrollPosition(2, 8), ScrollPosition(2, 30), true)).isFalse()
}

@Test fun scrollingUp_showsFab() {
    assertThat(replyFabVisibleAfterScroll(ScrollPosition(3, 2), ScrollPosition(2, 80), false)).isTrue()
}

@Test fun unchangedPosition_keepsPreviousState() {
    assertThat(replyFabVisibleAfterScroll(ScrollPosition(2, 8), ScrollPosition(2, 8), false)).isFalse()
}
```

- [ ] **Step 2：运行测试确认失败，然后实现纯函数**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.topicdetail.ReplyFabVisibilityTest"`

Expected: 首次 `FAILED`；实现后 `BUILD SUCCESSFUL`。

```kotlin
data class ScrollPosition(val itemIndex: Int, val itemOffset: Int) : Comparable<ScrollPosition> {
    override fun compareTo(other: ScrollPosition): Int =
        compareValuesBy(this, other, ScrollPosition::itemIndex, ScrollPosition::itemOffset)
}

fun replyFabVisibleAfterScroll(
    previous: ScrollPosition,
    current: ScrollPosition,
    previousVisible: Boolean,
): Boolean = when {
    current > previous -> false
    current < previous -> true
    else -> previousVisible
}
```

- [ ] **Step 3：扩展 ReplyItem 操作参数**

保持默认参数，避免破坏其他调用方；头像/作者信息后的按钮点击区域不小于 48 dp：

```kotlin
fun ReplyItem(
    reply: Reply,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    isTopicAuthor: Boolean = false,
    showDirectReplyAction: Boolean = false,
    onMoreClick: () -> Unit = {},
    onReplyClick: () -> Unit = {},
    onReferenceClick: (ReplyReference) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onUrlClick: (String) -> Boolean = { false },
)
```

`showDirectReplyAction=false` 显示 `MoreVert`，否则显示 `Reply`，contentDescription 分别为“更多操作”和“回复 @用户名 #楼层”。

- [ ] **Step 4：实现允许背景交互的持久回复 Sheet**

不使用有遮罩的 `ModalBottomSheet`；`BoxWithConstraints` 计算 50% 上限，`OutlinedTextField(minLines=5)` 随内容增长，到上限后内部滚动。通过 `WindowInsets.ime` 和 `navigationBarsPadding()` 处理键盘与系统栏：

```kotlin
@Composable
fun ReplyEditorBottomSheet(
    state: ReplyEditorUiState,
    onEvent: (ReplyEditorUiEvent) -> Unit,
    onPickImage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val maximumSheetHeight = maxHeight * 0.5f
        AnimatedVisibility(
            visible = state.isOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maximumSheetHeight)
                    .navigationBarsPadding()
                    .animateContentSize(),
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                shape = MaterialTheme.shapes.extraLarge.copy(
                    bottomStart = CornerSize(0.dp),
                    bottomEnd = CornerSize(0.dp),
                ),
            ) {
                ReplyEditorContent(state, onEvent, onPickImage)
            }
        }
    }
}
```

编辑区包括字符数、系统选图、清空、关闭、发布、上传/发布进度、错误信息和图库跳转按钮；上传时 TextField、选图和发布均禁用。点击图片时先检查 `state.isLoggedIn`：已登录才启动系统选择器，未登录直接执行 `onLoginClick`，不会先读取本地图片。

- [ ] **Step 5：运行受影响 JVM 测试与编译**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.ui.ReplyItem*Test" --tests "app.mystery0.nodeflow.feature.topicdetail.ReplyFabVisibilityTest"`

Run: `./gradlew.bat :app:compileDebugKotlin`

Expected: 两条命令均 `BUILD SUCCESSFUL`。

---

### Task 7：整合主题详情、操作 Sheet、导航与成功定位

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailUiState.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailUiEvent.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowDestinations.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/navigation/NodeFlowNavHost.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt`
- Delete: `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorScreen.kt`
- Delete: `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorUiEvent.kt`
- Delete: `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorUiState.kt`
- Delete: `app/src/main/java/app/mystery0/nodeflow/feature/editor/EditorViewModel.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Tasks 5–6 的回复 ViewModel、Sheet、FAB 纯函数和 ReplyItem 回调。
- Produces: 主题详情中的完整回复入口、登录回跳、图库外链和提交成功刷新定位。

- [ ] **Step 1：写失败的主题刷新定位测试**

```kotlin
@Test
fun replyCreated_forcesRefreshAndPublishesFloorTarget() = runTest(testDispatcher) {
    val repository = FakeTopicRepository(successfulResponses())
    val viewModel = viewModel(repository)
    advanceUntilIdle()
    viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(floor = 8))
    advanceUntilIdle()
    assertThat(repository.requests.last()).isEqualTo(1221181L to true)
    assertThat(viewModel.uiState.value.replyFloorTarget).isEqualTo(8)
    viewModel.onEvent(TopicDetailUiEvent.ReplyFloorTargetConsumed)
    assertThat(viewModel.uiState.value.replyFloorTarget).isNull()
}
```

- [ ] **Step 2：运行 TopicDetail ViewModel 测试并确认失败**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.topicdetail.TopicDetailViewModelTest"`

Expected: `FAILED`，新事件和状态字段尚不存在。

- [ ] **Step 3：实现刷新定位事件**

```kotlin
sealed interface TopicDetailUiEvent {
    data object Refresh : TopicDetailUiEvent
    data object Retry : TopicDetailUiEvent
    data class ReplyCreated(val floor: Int) : TopicDetailUiEvent
    data object ReplyFloorTargetConsumed : TopicDetailUiEvent
}

data class TopicDetailUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val detail: TopicDetail? = null,
    val errorMessage: String? = null,
    val replyFloorTarget: Int? = null,
)
```

`ReplyCreated` 先保存目标楼层再 `load(forceRefresh=true)`；UI 完成滚动和 1.4 秒高亮后发送 `ReplyFloorTargetConsumed`。

- [ ] **Step 4：在主题详情整合三个层级**

根 `Box` 内依次放 Scaffold 内容、带动画的 FAB、持久回复 Sheet；只在非编辑状态保留 FAB 的滚动响应。回复项操作用局部 `selectedReply` 驱动 `ModalBottomSheet`：

```kotlin
var selectedReply by remember { mutableStateOf<Reply?>(null) }

ReplyItem(
    reply = reply,
    showDirectReplyAction = replyEditorState.isOpen,
    onMoreClick = { selectedReply = reply },
    onReplyClick = {
        onReplyEditorEvent(
            ReplyEditorUiEvent.OpenFloorReply(reply.author.username, reply.floor),
        )
    },
)
```

操作 Sheet 提供“回复”和禁用的“感谢 · 暂未开放”；选择回复先关闭操作 Sheet，再派发 `OpenFloorReply`。FAB 使用 `AnimatedVisibility` 的位移、淡入淡出和缩放组合；回复 Sheet 打开时强制隐藏。系统图片选择器使用：

```kotlin
val imagePicker = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
) { uri ->
    uri?.let { onReplyEditorEvent(ReplyEditorUiEvent.ImageSelected(it.toString())) }
}
```

- [ ] **Step 5：处理返回键、生命周期补写和一次性 Effect**

回复 Sheet 打开时注册 `BackHandler`：键盘由系统先消费，键盘已关闭后派发 `Close`。使用 `LifecycleEventObserver` 在 `ON_STOP` 派发 `FlushDraft`。NavHost 收集 Effect：

```kotlin
LaunchedEffect(replyEditorViewModel) {
    replyEditorViewModel.effects.collect { effect ->
        when (effect) {
            ReplyEditorEffect.RequestLogin -> navController.navigate(NodeFlowDestinations.Auth)
            ReplyEditorEffect.OpenGallery -> uriHandler.openUri("https://www.v2ex.com/i")
            is ReplyEditorEffect.ReplyCreated -> topicDetailViewModel.onEvent(
                TopicDetailUiEvent.ReplyCreated(effect.floor),
            )
        }
    }
}
```

- [ ] **Step 6：删除占位 Editor 路由和文件**

从 `NodeFlowDestinations` 删除 `Editor`，从 NavHost 删除其 composable，从 FeatureModule 删除 `EditorViewModel`。确认没有引用：

Run: `rg -n "feature\.editor|EditorViewModel|NodeFlowDestinations\.Editor|编辑器预留" app/src`

Expected: 无输出。

- [ ] **Step 7：运行主题测试和 Debug 编译**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.topicdetail.*Test"`

Run: `./gradlew.bat :app:assembleDebug`

Expected: 两条命令均 `BUILD SUCCESSFUL`。

---

### Task 8：文档、全量验证与模拟器验收

**Files:**
- Modify: `docs/index.md`
- Verify: `docs/plans/2026-07-18-v2ex-create-reply-design.md`
- Verify: `app/schemas/app.mystery0.nodeflow.core.database.NodeFlowDatabase/4.json`

**Interfaces:**
- Consumes: Tasks 1–7 的完整功能。
- Produces: 可复查的自动化、构建、lint、模拟器和外部写入边界结果。

- [ ] **Step 1：更新文档索引配对链接**

```markdown
- [V2EX 创建回复设计](plans/2026-07-18-v2ex-create-reply-design.md) / [实施计划](plans/2026-07-18-v2ex-create-reply.md)
```

- [ ] **Step 2：运行相关局部测试**

Run: `./gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest" --tests "app.mystery0.nodeflow.data.reply.*" --tests "app.mystery0.nodeflow.feature.replyeditor.*" --tests "app.mystery0.nodeflow.feature.topicdetail.*"`

Expected: `BUILD SUCCESSFUL`，所有目标测试通过。

- [ ] **Step 3：运行全量 JVM 测试、构建与 lint**

Run: `./gradlew.bat :app:testDebugUnitTest`

Run: `./gradlew.bat :app:assembleDebug`

Run: `./gradlew.bat :app:lintDebug`

Expected: 三条命令均 `BUILD SUCCESSFUL`；如 lint 暴露既有问题，区分本次引入与既有基线并只修复本次问题。

- [ ] **Step 4：运行数据库 instrumentation 测试**

Run: `adb devices`

Expected: 至少一个状态为 `device` 的模拟器。

Run: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.mystery0.nodeflow.core.database.ReplyDraftMigrationTest`

Expected: `BUILD SUCCESSFUL`。

- [ ] **Step 5：安装并执行不产生外部写入的模拟器验收**

Run: `./gradlew.bat :app:installDebug`

在用户现有登录会话下只做本地交互，不点击最终发布、不选择会立即上传的真实图片：

1. 打开任意主题，向下滚动确认 FAB 位移/淡出/缩放隐藏，向上滚动确认显示。
2. 点击 FAB，确认 Sheet 初始输入区约 5 行、帖子背景仍能滚动、FAB 被遮挡状态不闪烁。
3. 输入超过 5 行，确认 Sheet 自动增高且不超过可用屏幕 50%，继续输入后输入框内部滚动。
4. 打开/关闭 IME，检查系统栏、导航栏和返回键；关闭 Sheet 后草稿保留。
5. 非编辑状态点回复项三点，确认“回复”可用、“感谢 · 暂未开放”禁用。
6. 编辑状态点多个楼层的直接回复按钮，确认都插入当前光标，紧邻同一引用不重复。
7. 切换深色模式、Dynamic Color、横竖屏和较大字体，检查布局与 48 dp 点击区域。
8. 强制停止并重新启动应用，确认正文和选区恢复；图片记录恢复由不产生外部上传的 Room instrumentation 测试覆盖。

- [ ] **Step 6：检查最终差异与敏感数据**

Run: `git diff --check`

Run: `git status --short`

Run: `rg -n "Cookie:|session_cookie|once=[0-9]+|password|url_o.*i\.v2ex\.co" app/src docs --glob '!docs/plans/2026-07-18-v2ex-create-reply-design.md'`

Expected: 无空白错误；差异只包含本功能及此前已确认的设计文档；无真实凭据、回复正文或上传结果。

- [ ] **Step 7：在执行真实回复前向用户索取测试主题**

向用户说明自动化和模拟器本地交互结果，并询问“请提供用于真实回复验证的 V2EX 主题链接或主题 ID，以及允许发布的测试文字”。未取得目标和文字前停止真实写入；用户授权后只提交一次，不自动重试，完成后报告楼层与是否需要用户自行清理测试回复。

---

### Task 9：修正回复 Sheet 底部安全区背景

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/androidTest/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorBottomSheetTest.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/replyeditor/ReplyEditorBottomSheet.kt`

**Interfaces:**
- Consumes: `ReplyEditorBottomSheet(state, onEvent, onPickImage, onLoginClick)` 与 `WindowInsets.navigationBars`。
- Produces: Surface 覆盖屏幕底边、内容避让导航栏的回复编辑器布局。

- [ ] **Step 1：编写失败的 Compose 仪器测试**

使用绿色详情背景和红色 Sheet Surface 渲染打开状态的 `ReplyEditorBottomSheet`，推进进入动画后捕获根节点图像，断言底部中央像素不再是绿色详情背景。测试必须在修改生产布局前运行，并因当前 Surface 外层的透明导航栏 padding 失败。

- [ ] **Step 2：运行测试确认失败原因**

Run: `./gradlew.bat :app:connectedDebugAndroidTest`

Expected: FAIL，底部中央像素仍为详情背景色；不得因依赖、编译或选择器错误失败。

- [ ] **Step 3：实现最小布局修正**

从 `Surface` 的 modifier 移除 `.navigationBarsPadding()`，将它添加到 Surface 内部可滚动 `Column` 的 modifier。不得修改导航栏全局颜色、Sheet 高度策略、背景交互或其他回复逻辑。

- [ ] **Step 4：运行局部测试确认通过**

Run: `./gradlew.bat :app:connectedDebugAndroidTest`

Expected: PASS，底部中央像素由 Sheet 背景覆盖。

- [ ] **Step 5：执行回归验证**

Run: `./gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:lintDebug`

Expected: `BUILD SUCCESSFUL`。

安装最新 Debug APK，在 Pixel 10 Pro Android 17 模拟器打开回复 Sheet，分别检查手势导航和三键导航：底部不露出帖子背景，顶部仍有圆角，Sheet 上方的帖子仍可见且可滚动。

---

## 自审清单

- 设计中的主题回复、楼层回复、滚动 FAB、两种回复项入口、持久 Sheet、5 行到半屏、背景滚动、纯文本和光标插图均由 Tasks 5–7 覆盖。
- V2EX 图库权限、6 MB/四种格式、`qqfile`、字符串成功值、HTTPS 原图、铜币风险、不自动重试和不远端删除由 Tasks 1、2、4 覆盖。
- 动态回复表单、最新隐藏字段、Origin/Referer、单次 POST、登录/受限/Cloudflare/Anti-Flood、提交回读和无法确认保留由 Tasks 1、2、5 覆盖。
- 主题单草稿、选区、图片记录、防抖、关闭补写、成功/清空删除、失败保留、3→4 Migration 和 schema 由 Tasks 3–5 覆盖。
- 成功刷新、定位高亮、登录回跳、深色/Dynamic Color/IME/字体/进程恢复和真实写入授权边界由 Tasks 7–8 覆盖。
- 计划不包含发帖、Markdown 编辑器、感谢逻辑、自定义图床、批量选图、图库管理或自动删除远端图片。
