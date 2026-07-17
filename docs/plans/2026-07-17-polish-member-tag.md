# V2EX_Polish 用户标签兼容实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 启动时后台拉取 V2EX_Polish 插件存于记事本的用户标签数据并缓存到 DataStore，浏览内容时纯本地匹配，在回复行、帖子作者行、用户主页展示标签。

**Architecture:** `V2exRawApi` 拉 /notes 与 /notes/edit/{id} → `V2exHtmlParser` 定位 note 与提取原文 → `PolishMemberTagParser` 解析 member-tag → `MemberTagStore`（DataStore）缓存 → Repository/UseCase → AppViewModel 启动刷新 + `LocalMemberTags` 注入 UI。帖子加载路径零网络请求。

**Tech Stack:** Kotlin、Retrofit/OkHttp、Jsoup、kotlinx.serialization、DataStore、Koin、Compose、JUnit4+Truth。

**设计文档:** `docs/plans/2026-07-17-polish-member-tag-design.md`；数据格式见 `docs/investigations/2026-07-17-v2ex-polish-member-tag-format.md`。

## Global Constraints

- 沟通/注释中文，日志英文；分层 core/data/domain/feature；UI/ViewModel 不直接碰网络与 DataStore。
- 浏览帖子路径上绝不发起标签网络请求；仅启动（TTL 1 小时）与设置页手动同步触发。
- 登录页/受限页不当业务内容解析；解析失败一律降级为空映射不抛错。
- 不在测试/fixture/文档中保存真实 Cookie 与真实标签明细（fixture 用假名）。
- 测试 `.\gradlew.bat :app:testDebugUnitTest`；构建 `.\gradlew.bat :app:assembleDebug`。

---

### Task 1: PolishMemberTagParser

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/parser/PolishMemberTagParser.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/parser/PolishMemberTagParserTest.kt`

**Interfaces:**
- Produces: `PolishMemberTagParser.parse(content: String): Map<String, List<String>>`（Task 3/4 依赖）。

- [ ] **Step 1: 失败测试**

```kotlin
package app.mystery0.nodeflow.core.parser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PolishMemberTagParserTest {
    @Test
    fun parse_extractsMemberTags() {
        val content = "V2EX_Polish_settings" +
            """{"settings-sync":{"version":46},"options":{"foo":1},""" +
            """"member-tag":{"Alice":{"tags":["大佬","前端"],"avatar":"https://cdn.v2ex.com/a.png"},""" +
            """"bob":{"tags":["后端"],"avatar":"https://cdn.v2ex.com/b.png"}}}"""

        val tags = PolishMemberTagParser.parse(content)

        assertThat(tags).containsExactly(
            "Alice", listOf("大佬", "前端"),
            "bob", listOf("后端"),
        )
    }

    @Test
    fun parse_skipsEntriesWithoutTags() {
        val content = "V2EX_Polish_settings" +
            """{"member-tag":{"Alice":{"tags":[],"avatar":""},"bob":{"avatar":""}}}"""

        assertThat(PolishMemberTagParser.parse(content)).isEmpty()
    }

    @Test
    fun parse_returnsEmptyOnInvalidInput() {
        assertThat(PolishMemberTagParser.parse("")).isEmpty()
        assertThat(PolishMemberTagParser.parse("随便一条笔记")).isEmpty()
        assertThat(PolishMemberTagParser.parse("V2EX_Polish_settings not json")).isEmpty()
        assertThat(PolishMemberTagParser.parse("V2EX_Polish_settings{\"member-tag\":123}")).isEmpty()
        assertThat(PolishMemberTagParser.parse("V2EX_Polish_settings{}")).isEmpty()
    }
}
```

- [ ] **Step 2: 运行失败** `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.parser.PolishMemberTagParserTest"` → 编译失败。

- [ ] **Step 3: 实现**

```kotlin
package app.mystery0.nodeflow.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 V2EX_Polish 插件同步到记事本的用户标签数据。
 * note 原文 = 字面前缀 V2EX_Polish_settings + JSON，标签在 member-tag 键下。
 * 数据由插件写入，任何异常都降级为空映射。
 */
object PolishMemberTagParser {
    const val NOTE_PREFIX = "V2EX_Polish_settings"

    fun parse(content: String): Map<String, List<String>> {
        val trimmed = content.trim()
        if (!trimmed.startsWith(NOTE_PREFIX)) return emptyMap()
        val json = trimmed.removePrefix(NOTE_PREFIX)
        return runCatching {
            Json.parseToJsonElement(json).jsonObject["member-tag"]!!.jsonObject
                .mapValues { (_, entry) ->
                    entry.jsonObject["tags"]?.jsonArray
                        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        ?.filter(String::isNotBlank)
                        .orEmpty()
                }
                .filterValues { it.isNotEmpty() }
        }.getOrDefault(emptyMap())
    }
}
```

- [ ] **Step 4: 运行通过**（同 Step 2 命令）→ BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/parser/PolishMemberTagParser.kt app/src/test/java/app/mystery0/nodeflow/core/parser/PolishMemberTagParserTest.kt
git commit -m "feat: 解析 V2EX_Polish 用户标签数据"
```

---

### Task 2: V2exHtmlParser 记事本解析

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`（文件末尾追加两个方法）
- Test: `app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt`（追加）

**Interfaces:**
- Produces: `parsePolishNoteId(html: String): Long?`；`parseNoteEditContent(html: String): String?`（Task 3 依赖）。

- [ ] **Step 1: 失败测试（V2exHtmlParserTest 追加，fixture 用假数据）**

```kotlin
    @Test
    fun parsePolishNoteId_findsNoteByPrefix() {
        val html = """
            <div class="box">
              <div class="inner"><span class="bigger"><a href="/notes/11" class="black">普通笔记</a></span></div>
              <div class="inner"><span class="bigger"><a href="/notes/67331" class="black">V2EX_Polish_settings{&#34;settings-sync&#34;:{&#34;version&#34;:46（截断</a></span></div>
            </div>
        """.trimIndent()

        assertThat(parser.parsePolishNoteId(html)).isEqualTo(67331L)
    }

    @Test
    fun parsePolishNoteId_returnsNullWhenAbsent() {
        val html = """<div><a href="/notes/11" class="black">普通笔记</a></div>"""
        assertThat(parser.parsePolishNoteId(html)).isNull()
        assertThat(parser.parsePolishNoteId("<html><body>空页面</body></html>")).isNull()
    }

    @Test
    fun parseNoteEditContent_extractsTextareaWithEntitiesDecoded() {
        val html = """
            <form><textarea class="mle" name="content">V2EX_Polish_settings{&#34;member-tag&#34;:{}}</textarea></form>
        """.trimIndent()

        assertThat(parser.parseNoteEditContent(html))
            .isEqualTo("""V2EX_Polish_settings{"member-tag":{}}""")
    }

    @Test
    fun parseNoteEditContent_returnsNullWithoutTextarea() {
        assertThat(parser.parseNoteEditContent("<html><body>无</body></html>")).isNull()
    }
```

说明：现有测试类里 parser 实例的字段名以文件内既有写法为准（如 `private val parser = V2exHtmlParser()`），追加用例时沿用。

- [ ] **Step 2: 运行失败** `--tests "app.mystery0.nodeflow.core.parser.V2exHtmlParserTest"` → 编译失败。

- [ ] **Step 3: 实现（V2exHtmlParser 类内追加）**

```kotlin
    /** 在记事本列表页中定位 V2EX_Polish 数据 note，返回 note id；找不到返回 null。 */
    fun parsePolishNoteId(html: String): Long? {
        val document = Jsoup.parse(html, V2EX_BASE_URL)
        return document.select("a[href^=/notes/]")
            .firstOrNull { it.text().startsWith(PolishMemberTagParser.NOTE_PREFIX) }
            ?.attr("href")
            ?.substringAfterLast('/')
            ?.toLongOrNull()
    }

    /** 提取 note 编辑页 textarea 的完整原文（Jsoup 已做实体解码）；无 textarea 返回 null。 */
    fun parseNoteEditContent(html: String): String? =
        Jsoup.parse(html, V2EX_BASE_URL)
            .selectFirst("textarea")
            ?.wholeText()
            ?.takeIf { it.isNotBlank() }
```

如文件尚未引用 `PolishMemberTagParser`，添加 import `app.mystery0.nodeflow.core.parser.PolishMemberTagParser`（同包则无需）。

- [ ] **Step 4: 运行通过** → BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt app/src/test/java/app/mystery0/nodeflow/core/parser/V2exHtmlParserTest.kt
git commit -m "feat: 解析记事本列表与编辑页定位 Polish 数据"
```

---

### Task 3: 网络接口与 MemberTagRemoteDataSource

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/network/V2exRawApi.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/membertag/MemberTagRemoteDataSource.kt`

**Interfaces:**
- Consumes: Task 1/2 的解析函数。
- Produces: `MemberTagRemoteDataSource.fetchMemberTags(): Map<String, List<String>>`（Task 4 依赖；未登录抛 `NodeFlowException(Kind.Auth)`，无 note 返回空映射）。

无独立单测（薄编排层，遵循 NotificationRemoteDataSource 现状），由 Task 4 仓库测试与 Task 8 真机验证覆盖。

- [ ] **Step 1: V2exRawApi 追加（notifications 之后）**

```kotlin
    // 记事本页面用于读取 V2EX_Polish 插件同步的用户标签数据
    @GET("notes")
    suspend fun notesHtml(): Response<ResponseBody>

    @GET("notes/edit/{id}")
    suspend fun noteEditHtml(@Path("id") id: Long): Response<ResponseBody>
```

- [ ] **Step 2: 新建数据源（沿用通知页的重定向与页面分类防护）**

```kotlin
package app.mystery0.nodeflow.data.membertag

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.PolishMemberTagParser
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import okhttp3.ResponseBody
import retrofit2.Response

class MemberTagRemoteDataSource(
    private val api: V2exRawApi,
    private val parser: V2exHtmlParser,
) {
    /** 拉取并解析 Polish 用户标签；未安装插件/无 note 返回空映射。 */
    suspend fun fetchMemberTags(): Map<String, List<String>> = safeNetworkCall {
        val notesHtml = api.notesHtml().guardedHtml("/notes")
        val noteId = parser.parsePolishNoteId(notesHtml) ?: return@safeNetworkCall emptyMap()
        val editHtml = api.noteEditHtml(noteId).guardedHtml("/notes/edit/$noteId")
        val content = parser.parseNoteEditContent(editHtml) ?: return@safeNetworkCall emptyMap()
        PolishMemberTagParser.parse(content)
    }

    /** 校验最终地址与页面状态，登录页/受限页不当业务内容。 */
    private fun Response<ResponseBody>.guardedHtml(expectedPath: String): String {
        val url = raw().request.url
        if (url.scheme == "https" && url.host == "www.v2ex.com" && url.encodedPath == "/signin") {
            throw authInvalid()
        }
        if (url.scheme != "https" || url.host != "www.v2ex.com" || url.encodedPath != expectedPath) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "记事本页面地址异常",
            )
        }
        val html = bodyStringOrThrow()
        if (parser.hasSignInEntry(html)) throw authInvalid()
        if (parser.hasAccessChallenge(html)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.AccessDenied,
                message = "V2EX 暂时拒绝访问记事本",
            )
        }
        return html
    }

    private fun authInvalid() = NodeFlowException(
        kind = NodeFlowException.Kind.Auth,
        message = "登录状态已失效，请重新登录",
    )
}
```

- [ ] **Step 3: 编译** `.\gradlew.bat :app:assembleDebug` → BUILD SUCCESSFUL。

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/network/V2exRawApi.kt app/src/main/java/app/mystery0/nodeflow/data/membertag/MemberTagRemoteDataSource.kt
git commit -m "feat: 记事本接口与 Polish 标签远端数据源"
```

---

### Task 4: MemberTagStore 缓存、Repository、UseCase 与 Koin

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/datastore/MemberTagStore.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/membertag/MemberTagRepository.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/domain/membertag/MemberTagUseCases.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/membertag/MemberTagRepositoryImpl.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/RepositoryModule.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/DomainModule.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/datastore/DataStoreModule.kt`（注册 MemberTagStore；文件内按现有 SettingsStore/SessionStore 写法追加 `single { MemberTagStore(androidContext()) }`）
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/auth/AuthRepositoryImpl.kt`（登出清空标签缓存）
- Test: `app/src/test/java/app/mystery0/nodeflow/core/datastore/MemberTagSerializationTest.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/data/membertag/MemberTagRepositoryImplTest.kt`

**Interfaces:**
- Consumes: `MemberTagRemoteDataSource.fetchMemberTags()`（Task 3）、`SessionStore.session: Flow<AuthSession>`。
- Produces:
  - `MemberTagStore`：`observe(): Flow<CachedMemberTags>`、`save(tags, syncedAtEpochSeconds)`、`clear()`；`data class CachedMemberTags(val tags: Map<String, List<String>>, val syncedAtEpochSeconds: Long?)`
  - `MemberTagRepository`：`observeTags(): Flow<Map<String, List<String>>>`、`observeSyncedAt(): Flow<Long?>`、`suspend fun refresh(force: Boolean): Result<Unit>`
  - UseCase：`ObserveMemberTagsUseCase`、`ObserveMemberTagSyncedAtUseCase`、`RefreshMemberTagsUseCase`（`suspend operator fun invoke(force: Boolean = false): Result<Unit>`）
  - 序列化纯函数：`encodeMemberTags(Map<String, List<String>>): String`、`decodeMemberTags(String?): Map<String, List<String>>`

- [ ] **Step 1: 序列化失败测试**

```kotlin
package app.mystery0.nodeflow.core.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemberTagSerializationTest {
    @Test
    fun roundTrip() {
        val tags = mapOf("Alice" to listOf("大佬", "前端"), "bob" to listOf("后端"))
        assertThat(decodeMemberTags(encodeMemberTags(tags))).isEqualTo(tags)
    }

    @Test
    fun decode_invalidOrNullFallsBackToEmpty() {
        assertThat(decodeMemberTags(null)).isEmpty()
        assertThat(decodeMemberTags("")).isEmpty()
        assertThat(decodeMemberTags("not json")).isEmpty()
    }
}
```

- [ ] **Step 2: 运行失败** `--tests "app.mystery0.nodeflow.core.datastore.MemberTagSerializationTest"` → 编译失败。

- [ ] **Step 3: MemberTagStore 实现**

```kotlin
package app.mystery0.nodeflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** V2EX_Polish 用户标签的本地缓存（username → tags），浏览时只读本缓存。 */
data class CachedMemberTags(
    val tags: Map<String, List<String>>,
    val syncedAtEpochSeconds: Long?,
)

class MemberTagStore(
    private val context: Context,
) {
    fun observe(): Flow<CachedMemberTags> = context.nodeFlowDataStore.data.map { preferences ->
        CachedMemberTags(
            tags = decodeMemberTags(preferences[Keys.tags]),
            syncedAtEpochSeconds = preferences[Keys.syncedAt],
        )
    }

    suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long) {
        context.nodeFlowDataStore.edit { preferences ->
            preferences[Keys.tags] = encodeMemberTags(tags)
            preferences[Keys.syncedAt] = syncedAtEpochSeconds
        }
    }

    suspend fun clear() {
        context.nodeFlowDataStore.edit { preferences ->
            preferences.remove(Keys.tags)
            preferences.remove(Keys.syncedAt)
        }
    }

    private object Keys {
        val tags = stringPreferencesKey("polish_member_tags")
        val syncedAt = longPreferencesKey("polish_member_tags_synced_at")
    }
}

private val MemberTagJson = Json { ignoreUnknownKeys = true }
private val MemberTagMapSerializer =
    MapSerializer(String.serializer(), ListSerializer(String.serializer()))

internal fun encodeMemberTags(tags: Map<String, List<String>>): String =
    MemberTagJson.encodeToString(MemberTagMapSerializer, tags)

internal fun decodeMemberTags(raw: String?): Map<String, List<String>> =
    raw?.takeIf { it.isNotBlank() }
        ?.let { runCatching { MemberTagJson.decodeFromString(MemberTagMapSerializer, it) }.getOrNull() }
        .orEmpty()
```

- [ ] **Step 4: 序列化测试通过**（Step 2 命令）→ BUILD SUCCESSFUL。

- [ ] **Step 5: Repository 接口与 UseCase**

`domain/membertag/MemberTagRepository.kt`：

```kotlin
package app.mystery0.nodeflow.domain.membertag

import kotlinx.coroutines.flow.Flow

interface MemberTagRepository {
    fun observeTags(): Flow<Map<String, List<String>>>
    fun observeSyncedAt(): Flow<Long?>

    /** 拉取并写入缓存；force=false 时距上次同步不足 TTL 直接跳过；未登录跳过。 */
    suspend fun refresh(force: Boolean): Result<Unit>
}
```

`domain/membertag/MemberTagUseCases.kt`：

```kotlin
package app.mystery0.nodeflow.domain.membertag

import kotlinx.coroutines.flow.Flow

class ObserveMemberTagsUseCase(
    private val repository: MemberTagRepository,
) {
    operator fun invoke(): Flow<Map<String, List<String>>> = repository.observeTags()
}

class ObserveMemberTagSyncedAtUseCase(
    private val repository: MemberTagRepository,
) {
    operator fun invoke(): Flow<Long?> = repository.observeSyncedAt()
}

class RefreshMemberTagsUseCase(
    private val repository: MemberTagRepository,
) {
    suspend operator fun invoke(force: Boolean = false): Result<Unit> = repository.refresh(force)
}
```

- [ ] **Step 6: Repository 失败测试**

```kotlin
package app.mystery0.nodeflow.data.membertag

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AuthSession
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MemberTagRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()

    private fun repository(
        session: AuthSession = AuthSession(personalAccessToken = null, cookieHeader = "A2=x", username = "me"),
        cached: FakeMemberTagCache = FakeMemberTagCache(),
        fetch: suspend () -> Map<String, List<String>> = { mapOf("Alice" to listOf("大佬")) },
        nowEpochSeconds: () -> Long = { 10_000L },
    ) = MemberTagRepositoryImpl(
        sessionFlow = MutableStateFlow(session),
        cache = cached,
        fetchRemoteTags = fetch,
        ioDispatcher = dispatcher,
        nowEpochSeconds = nowEpochSeconds,
    )

    @Test
    fun refresh_fetchesAndSaves() = runTest(dispatcher) {
        val cache = FakeMemberTagCache()
        val result = repository(cached = cache).refresh(force = false)

        assertThat(result.isSuccess).isTrue()
        assertThat(cache.saved?.first).containsEntry("Alice", listOf("大佬"))
        assertThat(cache.saved?.second).isEqualTo(10_000L)
    }

    @Test
    fun refresh_skipsWithinTtlUnlessForced() = runTest(dispatcher) {
        var fetchCount = 0
        val cache = FakeMemberTagCache(syncedAt = 9_500L)
        val repo = repository(cached = cache, fetch = { fetchCount++; emptyMap() })

        assertThat(repo.refresh(force = false).isSuccess).isTrue()
        assertThat(fetchCount).isEqualTo(0)

        assertThat(repo.refresh(force = true).isSuccess).isTrue()
        assertThat(fetchCount).isEqualTo(1)
    }

    @Test
    fun refresh_skipsWhenLoggedOut() = runTest(dispatcher) {
        var fetchCount = 0
        val repo = repository(
            session = AuthSession(personalAccessToken = null, cookieHeader = null, username = null),
            fetch = { fetchCount++; emptyMap() },
        )

        assertThat(repo.refresh(force = true).isSuccess).isTrue()
        assertThat(fetchCount).isEqualTo(0)
    }

    @Test
    fun refresh_keepsCacheOnFailure() = runTest(dispatcher) {
        val cache = FakeMemberTagCache(tags = mapOf("Old" to listOf("旧")))
        val repo = repository(
            cached = cache,
            fetch = { throw NodeFlowException(kind = NodeFlowException.Kind.Network, message = "boom") },
        )

        assertThat(repo.refresh(force = true).isFailure).isTrue()
        assertThat(cache.saved).isNull()
        assertThat(cache.cleared).isFalse()
    }
}
```

`FakeMemberTagCache` 与仓库实现同文件测试目录，实现 `MemberTagCache` 接口（见 Step 7）：

```kotlin
class FakeMemberTagCache(
    tags: Map<String, List<String>> = emptyMap(),
    syncedAt: Long? = null,
) : MemberTagCache {
    private val state = kotlinx.coroutines.flow.MutableStateFlow(
        app.mystery0.nodeflow.core.datastore.CachedMemberTags(tags, syncedAt),
    )
    var saved: Pair<Map<String, List<String>>, Long>? = null
        private set
    var cleared: Boolean = false
        private set

    override fun observe() = state

    override suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long) {
        saved = tags to syncedAtEpochSeconds
        state.value = app.mystery0.nodeflow.core.datastore.CachedMemberTags(tags, syncedAtEpochSeconds)
    }

    override suspend fun clear() {
        cleared = true
    }
}
```

- [ ] **Step 7: Repository 实现**

为可测性，仓库依赖抽象缓存接口与函数型远端（放同文件）：

```kotlin
package app.mystery0.nodeflow.data.membertag

import app.mystery0.nodeflow.core.datastore.CachedMemberTags
import app.mystery0.nodeflow.core.datastore.MemberTagStore
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.domain.membertag.MemberTagRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** MemberTagStore 的抽象，便于单测替换。 */
interface MemberTagCache {
    fun observe(): Flow<CachedMemberTags>
    suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long)
    suspend fun clear()
}

class DataStoreMemberTagCache(private val store: MemberTagStore) : MemberTagCache {
    override fun observe(): Flow<CachedMemberTags> = store.observe()
    override suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long) =
        store.save(tags, syncedAtEpochSeconds)
    override suspend fun clear() = store.clear()
}

class MemberTagRepositoryImpl(
    private val sessionFlow: Flow<AuthSession>,
    private val cache: MemberTagCache,
    private val fetchRemoteTags: suspend () -> Map<String, List<String>>,
    private val ioDispatcher: CoroutineDispatcher,
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) : MemberTagRepository {
    override fun observeTags(): Flow<Map<String, List<String>>> =
        cache.observe().map { it.tags }

    override fun observeSyncedAt(): Flow<Long?> =
        cache.observe().map { it.syncedAtEpochSeconds }

    override suspend fun refresh(force: Boolean): Result<Unit> = withContext(ioDispatcher) {
        val session = sessionFlow.first()
        val loggedIn = !session.cookieHeader.isNullOrBlank() ||
            !session.personalAccessToken.isNullOrBlank()
        if (!loggedIn) return@withContext Result.success(Unit)
        if (!force) {
            val syncedAt = cache.observe().first().syncedAtEpochSeconds
            if (syncedAt != null && nowEpochSeconds() - syncedAt < REFRESH_TTL_SECONDS) {
                return@withContext Result.success(Unit)
            }
        }
        runCatching {
            val tags = fetchRemoteTags()
            cache.save(tags, nowEpochSeconds())
        }
    }

    private companion object {
        const val REFRESH_TTL_SECONDS = 60L * 60L
    }
}
```

- [ ] **Step 8: 运行仓库测试** `--tests "app.mystery0.nodeflow.data.membertag.MemberTagRepositoryImplTest"` → BUILD SUCCESSFUL。

- [ ] **Step 9: Koin 注册与登出清空**

`DataStoreModule.kt` 追加：`single { MemberTagStore(androidContext()) }`（import 按文件现状）。

`RepositoryModule.kt` 追加：

```kotlin
    single { app.mystery0.nodeflow.data.membertag.MemberTagRemoteDataSource(get(), get()) }

    single<app.mystery0.nodeflow.domain.membertag.MemberTagRepository> {
        val remote = get<app.mystery0.nodeflow.data.membertag.MemberTagRemoteDataSource>()
        app.mystery0.nodeflow.data.membertag.MemberTagRepositoryImpl(
            sessionFlow = get<app.mystery0.nodeflow.core.datastore.SessionStore>().session,
            cache = app.mystery0.nodeflow.data.membertag.DataStoreMemberTagCache(get()),
            fetchRemoteTags = { remote.fetchMemberTags() },
            ioDispatcher = get(named(IO_DISPATCHER)),
        )
    }
```

（实际编写时把长限定名改为文件顶部 import，保持与文件风格一致。）

`DomainModule.kt` 追加三个 factory：`ObserveMemberTagsUseCase(get())`、`ObserveMemberTagSyncedAtUseCase(get())`、`RefreshMemberTagsUseCase(get())`。

`AuthRepositoryImpl`：构造器追加 `private val memberTagStore: MemberTagStore`，`clearSession()` 中追加 `memberTagStore.clear()`；`RepositoryModule` 中 `AuthRepositoryImpl(get(), get(), get(), get(named(IO_DISPATCHER)))` 相应补一个 `get()`（顺序与构造器一致）。

- [ ] **Step 10: 全量测试与构建** `.\gradlew.bat :app:testDebugUnitTest` + `:app:assembleDebug` → 均 BUILD SUCCESSFUL。

- [ ] **Step 11: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/datastore/MemberTagStore.kt app/src/main/java/app/mystery0/nodeflow/core/datastore/DataStoreModule.kt app/src/main/java/app/mystery0/nodeflow/domain/membertag app/src/main/java/app/mystery0/nodeflow/data/membertag app/src/main/java/app/mystery0/nodeflow/data/RepositoryModule.kt app/src/main/java/app/mystery0/nodeflow/domain/DomainModule.kt app/src/main/java/app/mystery0/nodeflow/data/auth/AuthRepositoryImpl.kt app/src/test/java/app/mystery0/nodeflow/core/datastore/MemberTagSerializationTest.kt app/src/test/java/app/mystery0/nodeflow/data/membertag/MemberTagRepositoryImplTest.kt
git commit -m "feat: Polish 用户标签缓存仓库与启动刷新基础设施"
```

---

### Task 5: 设置开关 showMemberTags 与启动注入

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt`（AppSettings）
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/datastore/SettingsStore.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/settings/SettingsRepository.kt` / `data/settings/SettingsRepositoryImpl.kt` / `domain/settings/UpdateSettingsUseCase.kt`（各加 `setShowMemberTags(enabled: Boolean)` 透传）
- Create: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/MemberTags.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/AppViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/MainActivity.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/feature/home/HomeViewModelTest.kt`（Fake 仓库补方法）
- Test: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/MemberTagsTest.kt`

**Interfaces:**
- Consumes: Task 4 的 UseCase。
- Produces: `AppSettings.showMemberTags: Boolean = true`；`LocalMemberTags: ProvidableCompositionLocal<Map<String, List<String>>>`；`memberTagsFor(tags: Map<String, List<String>>, username: String): List<String>`（Task 6 依赖）。

- [ ] **Step 1: memberTagsFor 失败测试**

```kotlin
package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemberTagsTest {
    @Test
    fun memberTagsFor_matchesIgnoringCase() {
        val tags = mapOf("Alice" to listOf("大佬"))
        assertThat(memberTagsFor(tags, "alice")).containsExactly("大佬")
        assertThat(memberTagsFor(tags, "ALICE")).containsExactly("大佬")
    }

    @Test
    fun memberTagsFor_returnsEmptyWhenAbsentOrBlank() {
        val tags = mapOf("Alice" to listOf("大佬"))
        assertThat(memberTagsFor(tags, "bob")).isEmpty()
        assertThat(memberTagsFor(tags, "")).isEmpty()
        assertThat(memberTagsFor(emptyMap(), "Alice")).isEmpty()
    }
}
```

- [ ] **Step 2: 运行失败** → 编译失败。

- [ ] **Step 3: 实现**

`MemberTags.kt`：

```kotlin
package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * V2EX_Polish 用户标签（username → tags），由应用根部从本地缓存注入；
 * 开关关闭时注入空映射，展示侧无需感知开关。
 */
val LocalMemberTags: ProvidableCompositionLocal<Map<String, List<String>>> =
    compositionLocalOf { emptyMap() }

/** 按用户名查标签，V2EX 用户名不区分大小写。 */
fun memberTagsFor(tags: Map<String, List<String>>, username: String): List<String> {
    if (username.isBlank() || tags.isEmpty()) return emptyList()
    tags[username]?.let { return it }
    val lower = username.lowercase()
    return tags.entries.firstOrNull { it.key.lowercase() == lower }?.value.orEmpty()
}
```

`Models.kt` AppSettings 追加字段 `val showMemberTags: Boolean = true,`。

`SettingsStore`：`Keys` 加 `val showMemberTags = booleanPreferencesKey("polish_member_tags_enabled")`；`settings` Flow 构造加 `showMemberTags = preferences[Keys.showMemberTags] ?: true`；新增：

```kotlin
    suspend fun setShowMemberTags(enabled: Boolean) {
        context.nodeFlowDataStore.edit { preferences ->
            preferences[Keys.showMemberTags] = enabled
        }
    }
```

三层透传（与 setCustomImageHosts 完全同构）：接口/实现/UseCase 各加 `setShowMemberTags(enabled: Boolean)`。`HomeViewModelTest.FakeSettingsRepository` 补 `override suspend fun setShowMemberTags(enabled: Boolean) { settingsFlow.value = settingsFlow.value.copy(showMemberTags = enabled) }`。

`AppViewModel`：

```kotlin
class AppViewModel(
    observeSettings: ObserveSettingsUseCase,
    observeMemberTags: ObserveMemberTagsUseCase,
    refreshMemberTags: RefreshMemberTagsUseCase,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    // 开关关闭时对 UI 提供空映射，展示侧不感知开关
    val memberTags: StateFlow<Map<String, List<String>>> =
        combine(observeSettings(), observeMemberTags()) { current, tags ->
            if (current.showMemberTags) tags else emptyMap()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    init {
        // 启动后台刷新一次，TTL 由仓库控制；浏览路径不再触发网络
        viewModelScope.launch {
            refreshMemberTags()
        }
    }
}
```

（import：`app.mystery0.nodeflow.domain.membertag.ObserveMemberTagsUseCase`、`RefreshMemberTagsUseCase`、`kotlinx.coroutines.flow.combine`、`kotlinx.coroutines.launch`。）

AppViewModel 的 Koin 注册在 `app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt` 第 19 行：`AppViewModel(get())` 改为 `AppViewModel(get(), get(), get())`。

`MainActivity` setContent 内：

```kotlin
            val memberTags = appViewModel.memberTags.collectAsStateWithLifecycle()
```

`CompositionLocalProvider` 改为：

```kotlin
                CompositionLocalProvider(
                    LocalCustomImageHosts provides customImageHosts,
                    LocalMemberTags provides memberTags.value,
                ) {
```

（import `app.mystery0.nodeflow.core.designsystem.component.LocalMemberTags`。）

- [ ] **Step 4: 全量测试 + 构建** → BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt app/src/main/java/app/mystery0/nodeflow/core/datastore/SettingsStore.kt app/src/main/java/app/mystery0/nodeflow/domain/settings app/src/main/java/app/mystery0/nodeflow/data/settings/SettingsRepositoryImpl.kt app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/MemberTags.kt app/src/main/java/app/mystery0/nodeflow/AppViewModel.kt app/src/main/java/app/mystery0/nodeflow/MainActivity.kt app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt app/src/test/java/app/mystery0/nodeflow/feature/home/HomeViewModelTest.kt app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/MemberTagsTest.kt
git commit -m "feat: 用户标签开关、启动刷新与根部注入"
```

---

### Task 6: 三处 UI 展示

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/ui/MemberTagChips.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/ui/ReplyItem.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt`（TopicMetadataRow 处）
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/profile/ProfileScreen.kt`（ProfileHeader）

**Interfaces:**
- Consumes: `LocalMemberTags`、`memberTagsFor`（Task 5）。

- [ ] **Step 1: MemberTagChips 组件**

```kotlin
package app.mystery0.nodeflow.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** V2EX_Polish 用户标签徽标，多标签并列可换行；与「楼主」徽标以配色区分。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MemberTagChips(
    tags: List<String>,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 2: ReplyItem 接入**

用户名行改为 FlowRow 以支持标签换行（`import androidx.compose.foundation.layout.ExperimentalLayoutApi`、`FlowRow`、designsystem 的 `LocalMemberTags`/`memberTagsFor`）。把现有

```kotlin
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reply.author.username,
                            ...
                        )
                        if (isTopicAuthor) {
                            Spacer(Modifier.width(6.dp))
                            TopicAuthorBadge()
                        }
                    }
```

替换为：

```kotlin
                    val memberTags = memberTagsFor(LocalMemberTags.current, reply.author.username)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = reply.author.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (isTopicAuthor) {
                            TopicAuthorBadge()
                        }
                        memberTags.forEach { tag ->
                            MemberTagChip(tag)
                        }
                    }
```

其中 `MemberTagChip` 为单枚徽标；把 `MemberTagChips.kt` 中的单枚 Surface 抽成公开的 `MemberTagChip(tag: String)`，`MemberTagChips` 内部复用它（保持一处样式）。

- [ ] **Step 3: TopicMetadataRow 下方接入（TopicDetailScreen）**

在 `TopicMetadataRow` 的调用处之后（TopicDetailContent 顶部 Column 内，`TopicMetadataRow(...)` 紧接着）追加：

```kotlin
                    val authorTags = memberTagsFor(
                        LocalMemberTags.current,
                        detail.topic.author.username,
                    )
                    if (authorTags.isNotEmpty()) {
                        MemberTagChips(tags = authorTags)
                    }
```

import：`app.mystery0.nodeflow.core.designsystem.component.LocalMemberTags`、`app.mystery0.nodeflow.core.designsystem.component.memberTagsFor`、`app.mystery0.nodeflow.core.ui.MemberTagChips`。

- [ ] **Step 4: ProfileHeader 接入（ProfileScreen）**

`ProfileHeader` 中用户名 `Text` 之后追加：

```kotlin
        val memberTags = memberTagsFor(LocalMemberTags.current, user.username)
        if (memberTags.isNotEmpty()) {
            MemberTagChips(tags = memberTags)
        }
```

import 同上。

- [ ] **Step 5: 全量测试 + 构建** → BUILD SUCCESSFUL。

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/ui/MemberTagChips.kt app/src/main/java/app/mystery0/nodeflow/core/ui/ReplyItem.kt app/src/main/java/app/mystery0/nodeflow/feature/topicdetail/TopicDetailScreen.kt app/src/main/java/app/mystery0/nodeflow/feature/profile/ProfileScreen.kt
git commit -m "feat: 回复、帖子作者与用户主页展示 Polish 用户标签"
```

---

### Task 7: 设置界面「Polish 用户标签」条目

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsUiState.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsUiEvent.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsScreen.kt`
- Modify: SettingsViewModel 的 Koin 注册处（补依赖）

**Interfaces:**
- Consumes: `UpdateSettingsUseCase.setShowMemberTags`、`RefreshMemberTagsUseCase`、`ObserveMemberTagSyncedAtUseCase`（Task 4/5）。

- [ ] **Step 1: 状态与事件**

`SettingsUiState` 追加字段：

```kotlin
    val memberTagSyncedAtEpochSeconds: Long? = null,
    val isSyncingMemberTags: Boolean = false,
```

`SettingsUiEvent` 追加：

```kotlin
    data class MemberTagVisibilityChanged(val enabled: Boolean) : SettingsUiEvent
    data object SyncMemberTags : SettingsUiEvent
```

- [ ] **Step 2: ViewModel**

构造器追加 `observeMemberTagSyncedAt: ObserveMemberTagSyncedAtUseCase` 与 `private val refreshMemberTags: RefreshMemberTagsUseCase`；init 中追加：

```kotlin
        viewModelScope.launch {
            observeMemberTagSyncedAt().collectLatest { syncedAt ->
                _uiState.update { it.copy(memberTagSyncedAtEpochSeconds = syncedAt) }
            }
        }
```

onEvent 追加分支：

```kotlin
            is SettingsUiEvent.MemberTagVisibilityChanged -> viewModelScope.launch {
                updateSettings.setShowMemberTags(event.enabled)
            }
            SettingsUiEvent.SyncMemberTags -> viewModelScope.launch {
                _uiState.update { it.copy(isSyncingMemberTags = true, message = null) }
                val result = refreshMemberTags(force = true)
                _uiState.update {
                    it.copy(
                        isSyncingMemberTags = false,
                        message = if (result.isSuccess) "用户标签已同步" else "用户标签同步失败",
                    )
                }
            }
```

Koin 注册在 `feature/FeatureModule.kt` 第 43 行：`SettingsViewModel(get(), get(), get())` 按构造器顺序补两个 `get()` 成 `SettingsViewModel(get(), get(), get(), get(), get())`。

- [ ] **Step 3: 界面（「内容浏览」分组、图床条目之后）**

```kotlin
            state.settings.customImageHosts.forEach { ... }  // 既有代码之后
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Polish 用户标签") },
                supportingContent = {
                    val syncedText = state.memberTagSyncedAtEpochSeconds
                        ?.let { "上次同步：${formatEpochSeconds(it)}" }
                        ?: "未同步"
                    Text("展示 V2EX_Polish 插件为用户打的标签 · $syncedText")
                },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            enabled = !state.isSyncingMemberTags,
                            onClick = { onEvent(SettingsUiEvent.SyncMemberTags) },
                        ) { Text(if (state.isSyncingMemberTags) "同步中" else "同步") }
                        Switch(
                            checked = state.settings.showMemberTags,
                            onCheckedChange = {
                                onEvent(SettingsUiEvent.MemberTagVisibilityChanged(it))
                            },
                        )
                    }
                },
            )
```

import：`app.mystery0.nodeflow.core.ui.formatEpochSeconds`（Row/Alignment 已有）。

- [ ] **Step 4: 全量测试 + 构建** → BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/feature/settings app/src/main/java/app/mystery0/nodeflow/feature/FeatureModule.kt
git commit -m "feat: 设置界面管理 Polish 用户标签开关与手动同步"
```

---

### Task 8: 全量验证、模拟器检查与文档

**Files:**
- Modify: `docs/subsystems/content-rendering.md`、`docs/subsystems/storage.md`、`docs/index.md`

- [ ] **Step 1: 全量** `testDebugUnitTest` + `assembleDebug` → BUILD SUCCESSFUL。

- [ ] **Step 2: 模拟器验证**

1. `installDebug` 后冷启动，进设置确认「Polish 用户标签」条目出现且显示同步时间（启动刷新已触发）。
2. 从缓存里取一个已打标签的用户名（`adb shell run-as app.mystery0.nodeflow` 读 DataStore 或手动同步后看设置时间变化），deep link 打开其用户主页验证标签显示。
3. 打开该用户参与的帖子（或其主页最近回复入口）验证回复行标签与楼主徽标并列展示。
4. 设置关闭开关 → 标签消失；开启恢复；点「同步」出现成功 Snackbar。
5. 连续打开多个帖子后回设置确认「上次同步」时间未变化（证明浏览路径不触发拉取）。

- [ ] **Step 3: 文档**

`content-rendering.md`「富文本与链接」后适当位置（或 UI 小节）追加：

```markdown
- V2EX_Polish 用户标签：启动时后台同步记事本中的插件数据到本地缓存
  （`MemberTagRepository`），回复行、帖子作者行与用户主页经 `LocalMemberTags`
  纯本地匹配展示；浏览路径不发起标签网络请求。
```

`storage.md` DataStore 小节追加：

```markdown
- Polish 用户标签缓存存于 `polish_member_tags`（JSON）与 `polish_member_tags_synced_at`；
  展示开关为 `polish_member_tags_enabled`；登出时清空标签缓存。
```

`docs/index.md` 设计条目补充：`[V2EX_Polish 用户标签兼容设计](...) / [实施计划](plans/2026-07-17-polish-member-tag.md)`。

- [ ] **Step 4: Commit**

```bash
git add docs/subsystems/content-rendering.md docs/subsystems/storage.md docs/index.md
git commit -m "docs: 补充 Polish 用户标签子系统说明"
```
