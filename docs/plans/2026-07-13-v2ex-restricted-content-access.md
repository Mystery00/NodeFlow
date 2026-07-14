# V2EX Restricted Content Access Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复受限归档主题的正文选择，并让主题详情和节点列表在未登录或账号无权访问时显示明确提示，不再被 JSON 回退、缓存或重定向后的无关页面掩盖。

**Architecture:** 在 core/network 增加统一的 V2EX HTML 访问判定，以 Retrofit 最终响应 URL 为主、真实登录表单 DOM 为辅，抛出独立的 AccessDenied 错误。主题解析器只优先读取 #Main 下的正文；主题数据源、仓库和 ViewModel 分层保证权限错误不会被 JSON、缓存或旧 UI 状态吞掉；节点列表复用相同判定并把重定向到首页视为无权限。

**Tech Stack:** Kotlin、Android ViewModel/StateFlow、Retrofit 3、OkHttp 5/MockWebServer、Jsoup、Kotlin Coroutines Test、JUnit 4、Truth、Android Gradle Plugin、ADB。

## Global Constraints

- 生成的代码注释和文档使用中文；如确需新增日志，日志内容使用英文，且不得输出 Cookie、令牌、完整响应正文或其他敏感信息。
- 严格执行 TDD：先新增一个会因当前行为失败的测试并实际看到预期失败，再做最小实现，最后运行聚焦测试和相关回归测试。
- 生产代码不得硬编码主题 1221181、节点 flamewar 或节点管理员说明文案；这些值只能出现在测试夹具和实机验收步骤中。
- 不新增数据库字段，不改变回复解析、富文本渲染、Paging/UI 组件结构，也不新增登录或账号切换流程。
- 所有用于验证权限/最终 URL 语义，或期望 HTML 成功通过访问判定的测试 Response，都必须显式构造 raw request URL；Retrofit 的 Response.success(body) 会生成 http://localhost/，不能用它模拟主题或节点最终 URL。仅验证普通 HTTP 失败映射的 Response.error fixture 可保持现状。
- 保留当前模拟器中的已登录账号：不得清除应用数据、退出登录或重装时使用 -d。
- 不处理用户主动删除的 password.txt；不得修改或提交用户已有的 docs/2026-07-13-v2ex-daily-check-in-investigation.md。
- 每个实现任务只提交该任务列出的文件。提交前运行 git diff --check，并用 git status --short 核对范围。

---

## Task 1: 修正主题正文选择并拒绝登录页伪正文

**Files:**

- Modify: app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt
- Modify: app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt
- Create: app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlPageClassifier.kt

**Interfaces:**

- Consumes: V2exHtmlParser.parseTopicHtml(topicId: Long, html: String): ParsedTopicHtml?
- Produces: 方法签名不变；正文优先选择 #Main .topic_content，仅在缺少该结构时回退全局 .topic_content；真实受限登录表单返回 null。
- Produces: internal fun Document.hasRestrictedSignInForm(): Boolean，供解析器和后续统一权限守卫共同使用。

- [ ] **Step 1: 在解析器测试中增加两个回归场景**

在 V2exHtmlParserTest 中加入：

    @Test
    fun parseTopicHtml_prefersMainTopicContentOverNodeNotice() {
        val html = """
            <html>
              <body>
                <div class="topic_content">
                  <p>这个节点的存在，只是为了将一类信息进行归类。</p>
                </div>
                <div id="Main">
                  <div class="box">
                    <div class="header">
                      <a href="/go/flamewar">水深火热</a>
                      <h1>受限归档主题</h1>
                      <small class="gray">
                        <a href="/member/alice">alice</a>
                      </small>
                    </div>
                    <div class="cell">
                      <div class="topic_content">
                        <p>真实主题正文</p>
                      </div>
                    </div>
                  </div>
                </div>
              </body>
            </html>
        """.trimIndent()

        val topic = parser.parseTopicHtml(topicId = 1221181, html = html)

        assertThat(topic).isNotNull()
        assertThat(topic!!.contentRendered).contains("真实主题正文")
        assertThat(topic.contentRendered).doesNotContain("这个节点的存在")
    }

    @Test
    fun parseTopicHtml_returnsNullForRestrictedSignInPageWithTopicContent() {
        val html = """
            <html>
              <body>
                <div id="problem" class="topic_content">需要登录后访问</div>
                <form action="/signin" method="post">
                  <input type="hidden" name="next" value="/restricted" />
                  <input type="password" name="password" />
                </form>
              </body>
            </html>
        """.trimIndent()

        assertThat(parser.parseTopicHtml(topicId = 1221181, html = html)).isNull()
    }

- [ ] **Step 2: 运行聚焦测试并确认 RED**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest.parseTopicHtml_prefersMainTopicContentOverNodeNotice" --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest.parseTopicHtml_returnsNullForRestrictedSignInPageWithTopicContent"

预期：两个新测试失败。第一个返回节点说明，第二个把 #problem.topic_content 当成了主题正文。

- [ ] **Step 3: 最小修改解析器**

在 parseTopicHtml 中把现有 document/contentElement 初始化替换为以下代码，先排除真实登录表单，再限定正文选择范围：

    val document = Jsoup.parse(html, V2EX_BASE_URL)
    if (document.hasRestrictedSignInForm()) return null
    val contentElement = document.selectFirst("#Main .topic_content")
        ?: document.selectFirst(".topic_content")

创建 V2exHtmlPageClassifier.kt，把登录表单 DOM 分类集中在一个可供同模块复用的内部扩展函数中：

    package app.mystery0.nodeflow.core.parser

    import org.jsoup.nodes.Document

    internal fun Document.hasRestrictedSignInForm(): Boolean =
        select("form[action='/signin']").any { form ->
            form.selectFirst("input[type=password]") != null ||
                form.selectFirst("input[type=hidden][name=next]")
                    ?.attr("value") == "/restricted"
        }

现有后续代码已经使用 contentElement，保持其余回复、分页和元数据逻辑不变。V2exHtmlParser.kt 与 V2exHtmlPageClassifier.kt 位于同一包，无需额外导入。

- [ ] **Step 4: 运行解析器回归测试并确认 GREEN**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest"

预期：V2exHtmlParserTest 全部通过，现有缺少 #Main 的片段测试仍由全局选择器兼容。

- [ ] **Step 5: 提交解析器修复**

执行：

    git diff --check
    git status --short
    git add app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlPageClassifier.kt app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt
    git commit -m "修复：限定主题正文解析范围"

---

## Task 2: 建立统一的 HTML 权限判定

**Files:**

- Modify: app/src/main/java/app/mystery0/nodeflow/core/common/NodeFlowException.kt
- Create: app/src/main/java/app/mystery0/nodeflow/core/network/V2exAccessGuard.kt
- Create: app/src/test/java/app/mystery0/nodeflow/core/network/V2exAccessGuardTest.kt

**Interfaces:**

- Consumes: Response<ResponseBody>.raw().request.url、Response<ResponseBody>.bodyStringOrThrow()、Jsoup HTML DOM 和 Document.hasRestrictedSignInForm()。
- Produces: NodeFlowException.Kind.AccessDenied。
- Produces: enum V2exHtmlAccessTarget { Topic, NodeTopics }。
- Produces: const val V2EX_ACCESS_DENIED_MESSAGE: String。
- Produces: fun Response<ResponseBody>.accessibleHtmlOrThrow(target: V2exHtmlAccessTarget): String；允许访问时返回且只返回响应正文，拒绝时抛出 NodeFlowException(AccessDenied)。

- [ ] **Step 1: 新增访问判定测试**

创建 V2exAccessGuardTest.kt。测试类使用现有 MockWebServer 依赖，不修改 Gradle：

    package app.mystery0.nodeflow.core.network

    import app.mystery0.nodeflow.core.common.NodeFlowException
    import com.google.common.truth.Truth.assertThat
    import kotlinx.coroutines.test.runTest
    import okhttp3.MediaType.Companion.toMediaType
    import okhttp3.Protocol
    import okhttp3.Request
    import okhttp3.Response as OkHttpResponse
    import okhttp3.ResponseBody
    import okhttp3.ResponseBody.Companion.toResponseBody
    import okhttp3.mockwebserver.MockResponse
    import okhttp3.mockwebserver.MockWebServer
    import org.junit.After
    import org.junit.Before
    import org.junit.Test
    import retrofit2.Response
    import retrofit2.Retrofit

    class V2exAccessGuardTest {
        private lateinit var server: MockWebServer
        private lateinit var api: V2exRawApi

        @Before
        fun setUp() {
            server = MockWebServer()
            server.start()
            api = Retrofit.Builder()
                .baseUrl(server.url("/"))
                .build()
                .create(V2exRawApi::class.java)
        }

        @After
        fun tearDown() {
            server.shutdown()
        }

        @Test
        fun topic_followsRedirectChainAndRejectsSignInFinalUrl() = runTest {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/restricted"),
            )
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .addHeader("Location", "/signin?next=%2Frestricted"),
            )
            server.enqueue(MockResponse().setBody("<html><body>Sign in</body></html>"))

            val response = api.topicHtml(topicId = 1221181)
            val result = runCatching {
                response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
            }

            assertThat(response.raw().request.url.encodedPath).isEqualTo("/signin")
            assertThat(response.raw().request.url.queryParameter("next")).isEqualTo("/restricted")
            val error = result.exceptionOrNull() as NodeFlowException
            assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
            assertThat(error.message).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
            assertThat(
                List(3) { server.takeRequest().path },
            ).containsExactly(
                "/t/1221181",
                "/restricted",
                "/signin?next=%2Frestricted",
            ).inOrder()
        }

        @Test
        fun topic_rejectsRestrictedFinalUrl() {
            val response = successfulHtmlResponse(
                html = "<html><body>Restricted</body></html>",
                finalUrl = "https://www.v2ex.com/restricted",
            )

            val result = runCatching {
                response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
            }

            val error = result.exceptionOrNull() as NodeFlowException
            assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        }

        @Test
        fun topic_rejectsPasswordSignInFormEvenAtTopicUrl() {
            val response = successfulHtmlResponse(
                html = """
                    <form action="/signin">
                      <input type="password" name="password" />
                    </form>
                """.trimIndent(),
                finalUrl = "https://www.v2ex.com/t/1221181",
            )

            val result = runCatching {
                response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
            }

            val error = result.exceptionOrNull() as NodeFlowException
            assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        }

        @Test
        fun topic_rejectsRestrictedNextSignInFormWithoutPassword() {
            val response = successfulHtmlResponse(
                html = """
                    <form action="/signin">
                      <input type="hidden" name="next" value="/restricted" />
                    </form>
                """.trimIndent(),
                finalUrl = "https://www.v2ex.com/t/1221181",
            )

            val result = runCatching {
                response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)
            }

            val error = result.exceptionOrNull() as NodeFlowException
            assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        }

        @Test
        fun topic_allowsPublicPageThatOnlyContainsSignInLink() {
            val html = """
                <a href="/signin">登录</a>
                <div id="Main">
                  <div class="topic_content">公开正文</div>
                </div>
            """.trimIndent()
            val response = successfulHtmlResponse(
                html = html,
                finalUrl = "https://www.v2ex.com/t/1",
            )

            val actual = response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)

            assertThat(actual).isEqualTo(html)
        }

        @Test
        fun nodeTopics_rejectsHomeRedirect() {
            val response = successfulHtmlResponse(
                html = "<html><body>Home topics</body></html>",
                finalUrl = "https://www.v2ex.com/",
            )

            val result = runCatching {
                response.accessibleHtmlOrThrow(V2exHtmlAccessTarget.NodeTopics)
            }

            val error = result.exceptionOrNull() as NodeFlowException
            assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        }

        private fun successfulHtmlResponse(
            html: String,
            finalUrl: String,
        ): Response<ResponseBody> {
            val rawResponse = OkHttpResponse.Builder()
                .request(Request.Builder().url(finalUrl).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                html.toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }
    }

- [ ] **Step 2: 运行新测试并确认 RED**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.network.V2exAccessGuardTest"

预期：编译失败，因为 AccessDenied、V2exHtmlAccessTarget、V2EX_ACCESS_DENIED_MESSAGE 和 accessibleHtmlOrThrow 尚不存在。

- [ ] **Step 3: 增加错误类型和访问判定实现**

在 NodeFlowException.Kind 中把 AccessDenied 放在 Auth 前：

    Parse,
    AccessDenied,
    Auth,
    Unknown,

创建 V2exAccessGuard.kt：

    package app.mystery0.nodeflow.core.network

    import app.mystery0.nodeflow.core.common.NodeFlowException
    import app.mystery0.nodeflow.core.parser.hasRestrictedSignInForm
    import okhttp3.ResponseBody
    import org.jsoup.Jsoup
    import retrofit2.Response

    enum class V2exHtmlAccessTarget {
        Topic,
        NodeTopics,
    }

    const val V2EX_ACCESS_DENIED_MESSAGE =
        "当前账号无权访问此内容；如果尚未登录，请登录具有访问权限的账号后重试。"

    fun Response<ResponseBody>.accessibleHtmlOrThrow(
        target: V2exHtmlAccessTarget,
    ): String {
        val finalPath = raw().request.url.encodedPath
        val deniedByUrl =
            finalPath == "/restricted" ||
                finalPath == "/signin" ||
                (target == V2exHtmlAccessTarget.NodeTopics && finalPath == "/")
        if (deniedByUrl) throw accessDenied()

        val html = bodyStringOrThrow()
        val deniedByDom = Jsoup.parse(html).hasRestrictedSignInForm()
        if (deniedByDom) throw accessDenied()
        return html
    }

    private fun accessDenied(): NodeFlowException =
        NodeFlowException(
            kind = NodeFlowException.Kind.AccessDenied,
            message = V2EX_ACCESS_DENIED_MESSAGE,
        )

URL 必须在读取 body 前判定；不要读取 Cookie、Authorization、重定向历史响应体或写诊断日志。

- [ ] **Step 4: 运行访问判定及网络回归测试并确认 GREEN**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.network.V2exAccessGuardTest" --tests "app.mystery0.nodeflow.core.network.V2exRawApiUserAgentTest"

预期：两个测试类全部通过；真实 302 链最终落到 /signin，并保留 next=/restricted。

- [ ] **Step 5: 提交统一访问判定**

执行：

    git diff --check
    git status --short
    git add app/src/main/java/app/mystery0/nodeflow/core/common/NodeFlowException.kt app/src/main/java/app/mystery0/nodeflow/core/network/V2exAccessGuard.kt app/src/test/java/app/mystery0/nodeflow/core/network/V2exAccessGuardTest.kt
    git commit -m "功能：识别 V2EX 受限 HTML 响应"

---

## Task 3: 让主题数据源透传权限错误并禁止 JSON 兜底

**Files:**

- Modify: app/src/main/java/app/mystery0/nodeflow/core/common/NodeFlowException.kt
- Modify: app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSource.kt
- Modify: app/src/test/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSourceTest.kt

**Interfaces:**

- Consumes: V2exRawApi.topicHtml、V2exHtmlParser.parseTopicHtml、V2exHtmlAccessTarget.Topic 和 accessibleHtmlOrThrow。
- Produces: fun Throwable.isAccessDenied(): Boolean。
- Produces: TopicRemoteDataSource.topicDetail(topicId: Long): TopicDetail 签名不变；AccessDenied 原样抛出且不调用 JSON topic/replies，其他 HTML 失败保持现有 JSON 兜底。

- [ ] **Step 1: 增加受限 URL 数据源测试**

在 TopicRemoteDataSourceTest 中加入以下导入：

    import app.mystery0.nodeflow.core.common.NodeFlowException
    import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
    import okhttp3.Protocol
    import okhttp3.Request
    import okhttp3.Response as OkHttpResponse

加入两个测试和共用断言：

    @Test
    fun topicDetail_doesNotFallBackToJsonWhenFinalUrlIsRestricted() = runTest {
        assertTopicAccessDeniedWithoutJson(
            finalUrl = "https://www.v2ex.com/restricted",
        )
    }

    @Test
    fun topicDetail_doesNotFallBackToJsonWhenFinalUrlIsSignIn() = runTest {
        assertTopicAccessDeniedWithoutJson(
            finalUrl = "https://www.v2ex.com/signin?next=%2Frestricted",
        )
    }

    private suspend fun assertTopicAccessDeniedWithoutJson(finalUrl: String) {
        val api = FakeV2exRawApi(
            topicHtmlPages = mapOf(
                null to """
                    <html><body>
                      <div id="problem" class="topic_content">Restricted</div>
                      <form action="/signin">
                        <input type="password" name="password" />
                      </form>
                    </body></html>
                """.trimIndent(),
            ),
            topicHtmlFinalUrls = mapOf(null to finalUrl),
            topicJson = """
                [{
                  "id": 1221181,
                  "title": "不应显示的 JSON 主题",
                  "content": "",
                  "content_rendered": "",
                  "replies": 25
                }]
            """.trimIndent(),
            repliesJson = "[]",
        )
        val dataSource = TopicRemoteDataSource(api, json, parser)

        val result = runCatching {
            dataSource.topicDetail(topicId = 1221181)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(error.message).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
        assertThat(api.topicJsonCalls).isEqualTo(0)
        assertThat(api.repliesJsonCalls).isEqualTo(0)
        assertThat(api.topicHtmlRequests).containsExactly(null)
    }

扩展 FakeV2exRawApi 构造参数：

    private val topicHtmlFinalUrls: Map<Int?, String> = emptyMap(),

把 topicHtml 实现替换为：

    override suspend fun topicHtml(
        topicId: Long,
        page: Int?,
    ): Response<ResponseBody> {
        topicHtmlRequests += page
        val defaultUrl = "https://www.v2ex.com/t/$topicId" +
            page?.let { "?p=$it" }.orEmpty()
        return htmlResponse(
            html = topicHtmlPages[page].orEmpty(),
            finalUrl = topicHtmlFinalUrls[page] ?: defaultUrl,
        )
    }

把 fake 内的 htmlResponse 扩展为可选最终 URL：

    private fun htmlResponse(
        html: String,
        finalUrl: String? = null,
    ): Response<ResponseBody> {
        val body = html.toResponseBody("text/html".toMediaType())
        if (finalUrl == null) return Response.success(body)
        val rawResponse = OkHttpResponse.Builder()
            .request(Request.Builder().url(finalUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        return Response.success(body, rawResponse)
    }

- [ ] **Step 2: 运行主题数据源测试并确认 RED**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.TopicRemoteDataSourceTest"

预期：两个新测试失败。解析器返回 null 后，当前 runCatching(...).getOrNull() 吞掉页面结果并调用 JSON topic/replies，不能得到 AccessDenied。

- [ ] **Step 3: 增加权限错误分类辅助函数**

在 NodeFlowException.kt 类定义后加入：

    fun Throwable.isAccessDenied(): Boolean =
        this is NodeFlowException &&
            kind == NodeFlowException.Kind.AccessDenied

- [ ] **Step 4: 所有主题 HTML 入口都先执行访问判定**

在 TopicRemoteDataSource.kt 增加导入：

    import app.mystery0.nodeflow.core.common.isAccessDenied
    import app.mystery0.nodeflow.core.network.V2exHtmlAccessTarget
    import app.mystery0.nodeflow.core.network.accessibleHtmlOrThrow

移除 bodyStringOrThrow 仅用于 topicHtml 的调用，但保留 JSON、recentTopicsHtml 等现有用途。将三类 topicHtml 读取分别改为：

    html = api.topicHtml(topicId)
        .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)

    html = api.topicHtml(topicId, page = page)
        .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)

    html = api.topicHtml(topicId)
        .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic)

对应位置依次是 HTML 第一页、后续回复页、JSON 路线的补充元数据请求。

- [ ] **Step 5: 只让 AccessDenied 穿透现有容错边界**

把顶层 HTML 尝试改为：

    val htmlDetail = runCatching {
        htmlTopicDetail(topicId)
    }.getOrElse { error ->
        if (error.isAccessDenied()) throw error
        null
    }

把后续页的 getOrNull 改为：

    val nextPage = runCatching {
        parser.parseTopicHtml(
            topicId = topicId,
            html = api.topicHtml(topicId, page = page)
                .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic),
        )
    }.getOrElse { error ->
        if (error.isAccessDenied()) throw error
        null
    } ?: continue

把 JSON 路线的 supplemental getOrNull 同样改为：

    val supplemental = runCatching {
        parser.parseTopicHtml(
            topicId = topicId,
            html = api.topicHtml(topicId)
                .accessibleHtmlOrThrow(V2exHtmlAccessTarget.Topic),
        )
    }.getOrElse { error ->
        if (error.isAccessDenied()) throw error
        null
    }

普通 HTTP、网络、解析或未知 HTML 仍按当前逻辑回退 JSON 或忽略补充元数据；只对 AccessDenied 禁止降级。

- [ ] **Step 6: 运行数据源测试并确认 GREEN**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.TopicRemoteDataSourceTest"

预期：受限 URL 测试得到 AccessDenied 且 JSON 调用为零；topicDetail_fallsBackToJsonWhenHtmlIsNotTopicPage 仍通过，证明普通未知 HTML 保持兼容。

- [ ] **Step 7: 提交主题数据源修复**

执行：

    git diff --check
    git status --short
    git add app/src/main/java/app/mystery0/nodeflow/core/common/NodeFlowException.kt app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSource.kt app/src/test/java/app/mystery0/nodeflow/data/topic/TopicRemoteDataSourceTest.kt
    git commit -m "修复：阻止受限主题回退 JSON"

---

## Task 4: 让权限错误绕过主题详情缓存

**Files:**

- Modify: app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImpl.kt
- Modify: app/src/test/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImplTest.kt

**Interfaces:**

- Consumes: TopicRemoteDataSource.topicDetail、TopicLocalDataSource.topicDetail/cacheTopicDetail 和 Throwable.isAccessDenied。
- Produces: TopicRepository.topicDetail(topicId: Long, forceRefresh: Boolean): Result<TopicDetail> 签名不变；每次先远端校验，AccessDenied 不回退缓存，其他失败仍可回退完整缓存。

- [ ] **Step 1: 增加完整缓存也必须远端校验的测试**

在 TopicRepositoryImplTest 增加导入：

    import app.mystery0.nodeflow.core.common.NodeFlowException
    import okhttp3.Protocol
    import okhttp3.Request
    import okhttp3.Response as OkHttpResponse

加入测试：

    @Test
    fun topicDetail_doesNotReturnCachedDetailWhenRemoteReportsAccessDenied() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val dao = FakeTopicDao()
        val api = AccessDeniedV2exRawApi()
        val repository = repository(api, dao, dispatcher)
        TopicLocalDataSource(dao).cacheTopicDetail(
            TopicDetail(
                topic = topic(id = 1221181, replyCount = 0),
                content = "旧缓存正文",
                contentRendered = "<p>旧缓存正文</p>",
                replies = emptyList(),
            ),
        )

        val result = repository.topicDetail(
            topicId = 1221181,
            forceRefresh = false,
        )

        assertThat(api.topicHtmlCalls).isEqualTo(1)
        assertThat(result.isFailure).isTrue()
        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

在 FailingV2exRawApi 后加入：

    private class AccessDeniedV2exRawApi : FailingV2exRawApi() {
        var topicHtmlCalls: Int = 0
            private set

        override suspend fun topicHtml(
            topicId: Long,
            page: Int?,
        ): Response<ResponseBody> {
            topicHtmlCalls += 1
            val rawResponse = OkHttpResponse.Builder()
                .request(
                    Request.Builder()
                        .url("https://www.v2ex.com/restricted")
                        .build(),
                )
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .build()
            return Response.success(
                "<html><body>Restricted</body></html>"
                    .toResponseBody("text/html".toMediaType()),
                rawResponse,
            )
        }
    }

同时把现有 SuccessV2exRawApi.topicHtml 改为带真实主题最终 URL 的成功响应，避免公共主题回归测试通过合成的 http://localhost/ 绕过 URL 语义：

    override suspend fun topicHtml(
        topicId: Long,
        page: Int?,
    ): Response<ResponseBody> {
        val finalUrl = "https://www.v2ex.com/t/$topicId" +
            page?.let { "?p=$it" }.orEmpty()
        val rawResponse = OkHttpResponse.Builder()
            .request(Request.Builder().url(finalUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        return Response.success(
            "<html><body></body></html>"
                .toResponseBody("text/html".toMediaType()),
            rawResponse,
        )
    }

- [ ] **Step 2: 运行仓库测试并确认 RED**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.TopicRepositoryImplTest"

预期：新测试失败，topicHtmlCalls 为 0 且仓库直接返回旧缓存，证明当前 forceRefresh=false 缓存短路绕过了权限校验。

- [ ] **Step 3: 移除缓存短路并禁止权限错误回退**

在 TopicRepositoryImpl.kt 增加：

    import app.mystery0.nodeflow.core.common.isAccessDenied

删除：

    if (!forceRefresh && usableCachedDetail != null) {
        return@runCatching usableCachedDetail
    }

把远端失败映射改为：

    runCatching { remoteDataSource.topicDetail(topicId) }
        .onSuccess { localDataSource.cacheTopicDetail(it) }
        .getOrElse { error ->
            if (error.isAccessDenied()) throw error
            usableCachedDetail ?: throw error
        }

保留现有缓存完整性条件。forceRefresh 参数继续保留以满足 TopicRepository 接口，但详情访问无论该值为何都要先获得当前会话的远端权限结论；普通错误仍可回退完整缓存。

- [ ] **Step 4: 运行仓库回归测试并确认 GREEN**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.topic.TopicRepositoryImplTest"

预期：新测试得到 AccessDenied；现有普通 500 回退零回复完整缓存的测试继续通过，不完整缓存仍不会被误当成详情。

- [ ] **Step 5: 提交缓存边界修复**

执行：

    git diff --check
    git status --short
    git add app/src/main/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImpl.kt app/src/test/java/app/mystery0/nodeflow/data/topic/TopicRepositoryImplTest.kt
    git commit -m "修复：权限错误绕过主题缓存"

---

## Task 5: 权限失败时清空主题详情状态

**Files:**

- Create: app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModelTest.kt
- Modify: app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModel.kt

**Interfaces:**

- Consumes: GetTopicDetailUseCase、Throwable.isAccessDenied 和现有 TopicDetailUiState。
- Produces: TopicDetailViewModel.uiState/onEvent 签名不变；AccessDenied 失败状态满足 detail == null 且 errorMessage 为统一权限提示，其他刷新失败继续保留 detail。

- [ ] **Step 1: 创建 ViewModel 状态测试**

创建 TopicDetailViewModelTest.kt：

    package app.mystery0.nodeflow.feature.topicdetail

    import androidx.lifecycle.SavedStateHandle
    import androidx.paging.PagingData
    import app.mystery0.nodeflow.core.common.NodeFlowException
    import app.mystery0.nodeflow.core.model.Node
    import app.mystery0.nodeflow.core.model.Topic
    import app.mystery0.nodeflow.core.model.TopicDetail
    import app.mystery0.nodeflow.core.model.User
    import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
    import app.mystery0.nodeflow.domain.topic.GetTopicDetailUseCase
    import app.mystery0.nodeflow.domain.topic.TopicRepository
    import com.google.common.truth.Truth.assertThat
    import java.util.ArrayDeque
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.ExperimentalCoroutinesApi
    import kotlinx.coroutines.flow.Flow
    import kotlinx.coroutines.flow.flowOf
    import kotlinx.coroutines.test.StandardTestDispatcher
    import kotlinx.coroutines.test.advanceUntilIdle
    import kotlinx.coroutines.test.resetMain
    import kotlinx.coroutines.test.runTest
    import kotlinx.coroutines.test.setMain
    import org.junit.After
    import org.junit.Before
    import org.junit.Test

    @OptIn(ExperimentalCoroutinesApi::class)
    class TopicDetailViewModelTest {
        private val testDispatcher = StandardTestDispatcher()

        @Before
        fun setUp() {
            Dispatchers.setMain(testDispatcher)
        }

        @After
        fun tearDown() {
            Dispatchers.resetMain()
        }

        @Test
        fun initialLoad_showsAccessDeniedAsFullPageState() = runTest(testDispatcher) {
            val repository = FakeTopicRepository(
                responses = ArrayDeque(
                    listOf(Result.failure<TopicDetail>(accessDenied())),
                ),
            )
            val viewModel = viewModel(repository)

            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.isRefreshing).isFalse()
            assertThat(state.detail).isNull()
            assertThat(state.errorMessage).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
            assertThat(repository.requests).containsExactly(1221181L to false)
        }

        @Test
        fun refresh_clearsExistingDetailWhenAccessIsDenied() = runTest(testDispatcher) {
            val original = topicDetail()
            val repository = FakeTopicRepository(
                responses = ArrayDeque(
                    listOf(
                        Result.success(original),
                        Result.failure<TopicDetail>(accessDenied()),
                    ),
                ),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()
            assertThat(viewModel.uiState.value.detail).isEqualTo(original)

            viewModel.onEvent(TopicDetailUiEvent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.isLoading).isFalse()
            assertThat(state.isRefreshing).isFalse()
            assertThat(state.detail).isNull()
            assertThat(state.errorMessage).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
            assertThat(repository.requests).containsExactly(
                1221181L to false,
                1221181L to true,
            ).inOrder()
        }

        @Test
        fun refresh_keepsExistingDetailWhenOtherFailureOccurs() = runTest(testDispatcher) {
            val original = topicDetail()
            val networkError = NodeFlowException(
                kind = NodeFlowException.Kind.Network,
                message = "网络连接失败，请稍后重试",
            )
            val repository = FakeTopicRepository(
                responses = ArrayDeque(
                    listOf(
                        Result.success(original),
                        Result.failure<TopicDetail>(networkError),
                    ),
                ),
            )
            val viewModel = viewModel(repository)
            advanceUntilIdle()

            viewModel.onEvent(TopicDetailUiEvent.Refresh)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertThat(state.detail).isEqualTo(original)
            assertThat(state.errorMessage).isEqualTo("网络连接失败，请稍后重试")
        }

        private fun viewModel(repository: TopicRepository): TopicDetailViewModel =
            TopicDetailViewModel(
                savedStateHandle = SavedStateHandle(
                    mapOf("topicId" to 1221181L),
                ),
                getTopicDetail = GetTopicDetailUseCase(repository),
            )

        private fun accessDenied(): NodeFlowException =
            NodeFlowException(
                kind = NodeFlowException.Kind.AccessDenied,
                message = V2EX_ACCESS_DENIED_MESSAGE,
            )

        private fun topicDetail(): TopicDetail =
            TopicDetail(
                topic = Topic(
                    id = 1221181,
                    title = "受限归档主题",
                    url = "https://www.v2ex.com/t/1221181",
                    node = Node(name = "flamewar", title = "水深火热"),
                    author = User(username = "alice"),
                    replyCount = 0,
                ),
                content = "真实正文",
                contentRendered = "<p>真实正文</p>",
                replies = emptyList(),
            )

        private class FakeTopicRepository(
            private val responses: ArrayDeque<Result<TopicDetail>>,
        ) : TopicRepository {
            val requests = mutableListOf<Pair<Long, Boolean>>()

            override suspend fun latestTopics(
                forceRefresh: Boolean,
            ): Result<List<Topic>> = Result.success(emptyList())

            override fun latestTopicsPaging(): Flow<PagingData<Topic>> =
                flowOf(PagingData.empty())

            override suspend fun topicDetail(
                topicId: Long,
                forceRefresh: Boolean,
            ): Result<TopicDetail> {
                requests += topicId to forceRefresh
                return responses.removeFirst()
            }

            override suspend fun clearCache() = Unit
        }
    }

- [ ] **Step 2: 运行 ViewModel 测试并确认 RED**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.topicdetail.TopicDetailViewModelTest"

预期：refresh_clearsExistingDetailWhenAccessIsDenied 失败，因为当前失败分支保留 current.detail；初次权限失败与普通网络刷新测试应通过，作为现有语义的保护。

- [ ] **Step 3: 只在 AccessDenied 时清除旧详情**

在 TopicDetailViewModel.kt 增加：

    import app.mystery0.nodeflow.core.common.isAccessDenied

将 onFailure 的 current.copy 改为：

    current.copy(
        isLoading = false,
        isRefreshing = false,
        detail = if (error.isAccessDenied()) null else current.detail,
        errorMessage = error.toUserMessage(),
    )

不要修改 TopicDetailUiState 或 TopicDetailScreen。现有 state.errorMessage != null && detail == null 分支会自动进入全页 ErrorContent；普通刷新错误仍显示详情上方错误条。

- [ ] **Step 4: 运行 ViewModel 测试并确认 GREEN**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.topicdetail.TopicDetailViewModelTest"

预期：三个测试全部通过。

- [ ] **Step 5: 提交 UI 状态边界修复**

执行：

    git diff --check
    git status --short
    git add app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModel.kt app/src/test/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailViewModelTest.kt
    git commit -m "修复：权限失败时清空主题详情"

---

## Task 6: 识别受限节点重定向且保持 Paging 错误语义

**Files:**

- Modify: app/src/test/java/app/mystery0/nodeflow/data/node/NodeRemoteDataSourceTest.kt
- Modify: app/src/main/java/app/mystery0/nodeflow/data/node/NodeRemoteDataSource.kt
- Modify: app/src/test/java/app/mystery0/nodeflow/data/topic/HomeTopicsPagingSourceTest.kt

**Interfaces:**

- Consumes: V2exRawApi.nodeTopicsHtml、V2exHtmlAccessTarget.NodeTopics 和 accessibleHtmlOrThrow。
- Produces: NodeRemoteDataSource.topics(name: String, page: Int): List<Topic> 签名不变；最终 URL 为首页/登录页/受限页时抛出 AccessDenied。
- Produces: HomeTopicsPagingSource 生产代码接口不变；其现有 LoadResult.Error 必须原样携带 AccessDenied，且失败时不缓存主题。

- [ ] **Step 1: 增加受限节点和公开节点测试**

在 NodeRemoteDataSourceTest 增加导入：

    import app.mystery0.nodeflow.core.common.NodeFlowException
    import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE
    import okhttp3.Protocol
    import okhttp3.Request
    import okhttp3.Response as OkHttpResponse

加入测试：

    @Test
    fun topics_throwsAccessDeniedWhenNodeRedirectsToHome() = runTest {
        val api = FakeV2exRawApi(
            nodeHtml = topicListHtml(topicId = 9001, title = "首页主题"),
            nodeTopicsFinalUrl = "https://www.v2ex.com/",
        )
        val dataSource = NodeRemoteDataSource(api, json, parser)

        val result = runCatching {
            dataSource.topics(name = "flamewar", page = 1)
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.AccessDenied)
        assertThat(error.message).isEqualTo(V2EX_ACCESS_DENIED_MESSAGE)
        assertThat(api.nodeTopicsHtmlRequests)
            .containsExactly(NodeTopicsHtmlRequest("flamewar", null))
    }

    @Test
    fun topics_parsesPublicNodeWhenFinalUrlRemainsNodePath() = runTest {
        val api = FakeV2exRawApi(
            nodeHtml = topicListHtml(topicId = 9002, title = "Android 主题"),
            nodeTopicsFinalUrl = "https://www.v2ex.com/go/android?p=2",
        )
        val dataSource = NodeRemoteDataSource(api, json, parser)

        val topics = dataSource.topics(name = "android", page = 2)

        assertThat(topics.map { it.id }).containsExactly(9002L)
        assertThat(topics.single().node.name).isEqualTo("android")
        assertThat(api.nodeTopicsHtmlRequests)
            .containsExactly(NodeTopicsHtmlRequest("android", 2))
    }

加入 HTML 夹具：

    private fun topicListHtml(topicId: Long, title: String): String =
        """
            <html><body>
              <div class="cell from_1 t_$topicId">
                <span class="item_title">
                  <a class="topic-link" href="/t/$topicId">$title</a>
                </span>
                <span class="topic_info">
                  <strong><a href="/member/alice">alice</a></strong>
                </span>
              </div>
            </body></html>
        """.trimIndent()

把 FakeV2exRawApi 构造参数改为带默认值，并增加最终 URL：

    private val nodeJson: String = "{}",
    private val nodeHtml: String = "",
    private val nodeTopicsFinalUrl: String? = null,

把 nodeTopicsHtml 的返回构造成带 raw URL：

    override suspend fun nodeTopicsHtml(
        nodeName: String,
        page: Int?,
    ): Response<ResponseBody> {
        nodeTopicsHtmlRequests += NodeTopicsHtmlRequest(nodeName, page)
        val defaultUrl = "https://www.v2ex.com/go/$nodeName" +
            page?.let { "?p=$it" }.orEmpty()
        return htmlResponse(
            html = nodeHtml,
            finalUrl = nodeTopicsFinalUrl ?: defaultUrl,
        )
    }

把 fake 的 htmlResponse 改为：

    private fun htmlResponse(
        html: String,
        finalUrl: String? = null,
    ): Response<ResponseBody> {
        val body = html.toResponseBody("text/html".toMediaType())
        if (finalUrl == null) return Response.success(body)
        val rawResponse = OkHttpResponse.Builder()
            .request(Request.Builder().url(finalUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
        return Response.success(body, rawResponse)
    }

- [ ] **Step 2: 增加 Paging 异常原样传播的特征测试**

在 HomeTopicsPagingSourceTest 增加 NodeFlowException 导入和测试：

    @Test
    fun load_preservesAccessDeniedErrorWithoutCaching() = runTest {
        val accessDenied = NodeFlowException(
            kind = NodeFlowException.Kind.AccessDenied,
            message = V2EX_ACCESS_DENIED_MESSAGE,
        )
        var cacheCallCount = 0
        val pagingSource = HomeTopicsPagingSource(
            loadTopics = { throw accessDenied },
            cacheTopics = { cacheCallCount += 1 },
        )

        val result = pagingSource.load(
            PagingSource.LoadParams.Refresh(
                key = null,
                loadSize = 20,
                placeholdersEnabled = false,
            ),
        )

        val error = result as PagingSource.LoadResult.Error
        assertThat(error.throwable).isSameInstanceAs(accessDenied)
        assertThat(cacheCallCount).isEqualTo(0)
    }

同时增加：

    import app.mystery0.nodeflow.core.common.NodeFlowException
    import app.mystery0.nodeflow.core.network.V2EX_ACCESS_DENIED_MESSAGE

- [ ] **Step 3: 运行节点与 Paging 测试并确认 RED**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.node.NodeRemoteDataSourceTest" --tests "app.mystery0.nodeflow.data.topic.HomeTopicsPagingSourceTest"

预期：topics_throwsAccessDeniedWhenNodeRedirectsToHome 失败，因为当前实现会把首页主题解析并强制标记为 flamewar；公开节点与 Paging 特征测试通过。

- [ ] **Step 4: 在节点列表解析前复用访问判定**

在 NodeRemoteDataSource.kt 增加：

    import app.mystery0.nodeflow.core.network.V2exHtmlAccessTarget
    import app.mystery0.nodeflow.core.network.accessibleHtmlOrThrow

把 topics 实现改为：

    suspend fun topics(name: String, page: Int): List<Topic> = safeNetworkCall {
        parser.parseTopicList(
            html = api.nodeTopicsHtml(
                nodeName = name,
                page = page.takeIf { it > 1 },
            ).accessibleHtmlOrThrow(V2exHtmlAccessTarget.NodeTopics),
            sourceNodeName = name,
        )
    }

不要把 NodeTopics 判定加到 node(name) 的补充详情请求；本次权限结论只控制节点主题列表主体。不要修改 HomeTopicsPagingSource 或节点 UI 的生产代码，现有 LoadResult.Error/LoadState.Error 会保留统一提示。

- [ ] **Step 5: 运行节点、Paging 和相关仓库测试并确认 GREEN**

执行：

    .\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.data.node.NodeRemoteDataSourceTest" --tests "app.mystery0.nodeflow.data.topic.HomeTopicsPagingSourceTest"

预期：全部通过，受限节点不会出现被误归类的首页主题，公开节点分页不变。

- [ ] **Step 6: 提交节点权限修复**

执行：

    git diff --check
    git status --short
    git add app/src/main/java/app/mystery0/nodeflow/data/node/NodeRemoteDataSource.kt app/src/test/java/app/mystery0/nodeflow/data/node/NodeRemoteDataSourceTest.kt app/src/test/java/app/mystery0/nodeflow/data/topic/HomeTopicsPagingSourceTest.kt
    git commit -m "修复：识别受限节点重定向"

---

## Task 7: 全量验证与模拟器验收

**Files:**

- Verify only: app/src/main
- Verify only: app/src/test
- Verify only: app/build/outputs/apk/debug/app-debug.apk

**Interfaces:**

- Consumes: Task 1 至 Task 6 的提交、Debug APK、MainActivity 的 V2EX 主题深链入口和当前模拟器登录会话。
- Produces: 无新代码接口；产出全量测试、构建、授权主题正文、外部深链和公开主题回归的验收证据。

- [ ] **Step 1: 运行全部 JVM 测试**

执行：

    .\gradlew.bat :app:testDebugUnitTest

预期：BUILD SUCCESSFUL，且 Task 1 至 Task 6 新增的所有测试均通过。

- [ ] **Step 2: 构建 Debug APK**

执行：

    .\gradlew.bat :app:assembleDebug

预期：BUILD SUCCESSFUL，并生成 app/build/outputs/apk/debug/app-debug.apk。

- [ ] **Step 3: 保留登录态覆盖安装**

先确认设备，再覆盖安装：

    adb devices
    adb install -r app\build\outputs\apk\debug\app-debug.apk

预期：模拟器状态为 device，安装输出 Success。不得执行 adb shell pm clear、卸载应用或退出账号。

- [ ] **Step 4: 通过外部深链打开受限归档主题**

执行：

    adb shell am force-stop app.mystery0.nodeflow
    adb shell am start -W -n app.mystery0.nodeflow/.MainActivity -a android.intent.action.VIEW -d "https://www.v2ex.com/t/1221181"

预期：当前已有权限的登录账号直接进入主题详情，标题与回复正常，显示 #Main 中的真实正文；正文不包含“这个节点的存在，只是为了将一类信息进行归类”等节点管理员说明。该命令同时覆盖浏览器/外部链接入口，应用内其他主题链接仍进入相同 TopicDetailViewModel 链路。

- [ ] **Step 5: 保存截图并人工核对授权场景**

执行：

    $shot = Join-Path $env:TEMP "nodeflow-topic-1221181-fixed.png"
    adb shell screencap -p /sdcard/nodeflow-topic-1221181-fixed.png
    adb pull /sdcard/nodeflow-topic-1221181-fixed.png "$shot"

使用图像查看工具核对正文和回复区域。若正文仍是节点说明，返回 Task 1 增加与真实 HTML 层级一致的失败夹具后再修复，不得直接扩大全局选择器。

- [ ] **Step 6: 冒烟验证公开主题**

执行：

    adb shell am start -W -n app.mystery0.nodeflow/.MainActivity -a android.intent.action.VIEW -d "https://www.v2ex.com/t/1100166"

预期：公开主题仍正常显示正文和回复，不出现权限提示。未登录/低权限分支由 MockWebServer 重定向链、数据源、仓库、ViewModel 与 Paging 测试覆盖；为保留用户当前授权账号，不在模拟器中主动退出登录。

- [ ] **Step 7: 最终检查工作区和提交历史**

执行：

    git diff --check
    git status --short
    git log --oneline -7

预期：没有未提交的实现改动；git status 只允许保留用户已有且未纳入本任务的 docs/2026-07-13-v2ex-daily-check-in-investigation.md。截图保存在系统临时目录，不纳入 Git。Task 1 至 Task 6 各有一个范围清晰的提交。
