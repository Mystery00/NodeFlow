# 自定义图床域名实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 设置界面可配置图床域名，内容中命中域名的链接按图片渲染（两条渲染管线），失败回退现有占位。

**Architecture:** 纯函数匹配器（core/link）+ DataStore 持久化（SettingsStore→Repository→UseCase）+ CompositionLocal 注入两条渲染管线（HtmlText/RichHtmlText）+ 设置界面域名列表管理。数据层与缓存零改动。

**Tech Stack:** Kotlin、Jetpack Compose、Jsoup、DataStore Preferences、Koin、JUnit4+Truth。

**设计文档:** `docs/plans/2026-07-17-custom-image-host-design.md`

## Global Constraints

- 沟通/注释/文档中文，日志英文；不新增 XML layout。
- 遵守 core/data/domain/feature 分层；UI/ViewModel 不直接碰 DataStore。
- 业务逻辑必须有单测；测试命令 `.\gradlew.bat :app:testDebugUnitTest`；构建 `.\gradlew.bat :app:assembleDebug`。
- 复用现有图片加载/失败占位/大图预览能力，不新增第二套实现。
- 匹配语义：配置 `example.com` 命中自身与子域名；仅 http/https。

---

### Task 1: 域名归一化与匹配器 ImageHostMatcher

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/link/ImageHostMatcher.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/link/ImageHostMatcherTest.kt`

**Interfaces:**
- Produces: `ImageHostMatcher.normalizeHost(input: String): String?`；`ImageHostMatcher.shouldLoadAsImage(url: String, hosts: Collection<String>): Boolean`（后续 Task 4/5/7 依赖）。

- [ ] **Step 1: 写失败测试**

```kotlin
package app.mystery0.nodeflow.core.link

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageHostMatcherTest {
    @Test
    fun normalizeHost_stripsSchemePathPortAndLowercases() {
        assertThat(ImageHostMatcher.normalizeHost("https://Img.Example.com/path?q=1#f"))
            .isEqualTo("img.example.com")
        assertThat(ImageHostMatcher.normalizeHost("http://example.com:8080/"))
            .isEqualTo("example.com")
        assertThat(ImageHostMatcher.normalizeHost("  example.com  ")).isEqualTo("example.com")
        assertThat(ImageHostMatcher.normalizeHost("localhost")).isEqualTo("localhost")
    }

    @Test
    fun normalizeHost_rejectsInvalidInput() {
        assertThat(ImageHostMatcher.normalizeHost("")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("   ")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("https://")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("exa mple.com")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("例子.com")).isNull()
        assertThat(ImageHostMatcher.normalizeHost(".example.com")).isNull()
    }

    @Test
    fun shouldLoadAsImage_matchesHostAndSubdomains() {
        val hosts = setOf("example.com")
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com/abc", hosts)).isTrue()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://img.example.com/abc", hosts)).isTrue()
        assertThat(ImageHostMatcher.shouldLoadAsImage("HTTPS://EXAMPLE.COM/ABC", hosts)).isTrue()
    }

    @Test
    fun shouldLoadAsImage_configuredSubdomainDoesNotMatchParent() {
        val hosts = setOf("img.example.com")
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://img.example.com/a", hosts)).isTrue()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com/a", hosts)).isFalse()
    }

    @Test
    fun shouldLoadAsImage_rejectsNonHttpAndLookalikes() {
        val hosts = setOf("example.com")
        assertThat(ImageHostMatcher.shouldLoadAsImage("ftp://example.com/a", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://fake-example.com/a", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com.evil.com/a", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("not a url", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com/a", emptySet())).isFalse()
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.link.ImageHostMatcherTest"`
Expected: 编译失败（ImageHostMatcher 不存在）。

- [ ] **Step 3: 最小实现**

```kotlin
package app.mystery0.nodeflow.core.link

import java.net.URI

/**
 * 自定义图床域名的归一化与匹配。配置 example.com 时命中自身与任意子域名；
 * 仅 http/https URL 参与匹配。
 */
object ImageHostMatcher {
    // 合法主机名：字母数字与连字符组成的标签，点分隔；单标签（如 localhost）也允许
    private val HOST_REGEX =
        Regex("""^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*$""")

    /** 用户输入 → 归一化域名；非法输入返回 null。 */
    fun normalizeHost(input: String): String? {
        val stripped = input.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .lowercase()
        return stripped.takeIf { it.isNotEmpty() && HOST_REGEX.matches(it) }
    }

    /** URL 主机命中任一配置域名（自身或其子域名）时按图片尝试加载。 */
    fun shouldLoadAsImage(url: String, hosts: Collection<String>): Boolean {
        if (hosts.isEmpty()) return false
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return false
        val host = uri.host?.lowercase() ?: return false
        return hosts.any { configured ->
            host == configured || host.endsWith(".$configured")
        }
    }
}
```

- [ ] **Step 4: 运行验证通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.link.ImageHostMatcherTest"`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/link/ImageHostMatcher.kt app/src/test/java/app/mystery0/nodeflow/core/link/ImageHostMatcherTest.kt
git commit -m "feat: 新增图床域名归一化与匹配器"
```

---

### Task 2: AppSettings 模型与 SettingsStore 持久化

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt`（AppSettings，第 9-13 行附近）
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/datastore/SettingsStore.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/datastore/CustomImageHostsSerializationTest.kt`

**Interfaces:**
- Consumes: 无。
- Produces: `AppSettings.customImageHosts: List<String>`；`SettingsStore.setCustomImageHosts(hosts: List<String>)`；纯函数 `encodeCustomImageHosts(hosts: List<String>): String` / `decodeCustomImageHosts(raw: String?): List<String>`（internal，供测试）。

- [ ] **Step 1: 写失败测试（序列化纯函数）**

```kotlin
package app.mystery0.nodeflow.core.datastore

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CustomImageHostsSerializationTest {
    @Test
    fun roundTrip_keepsOrderAndContent() {
        val hosts = listOf("example.com", "img.foo.net")
        assertThat(decodeCustomImageHosts(encodeCustomImageHosts(hosts))).isEqualTo(hosts)
    }

    @Test
    fun decode_handlesNullAndBlank() {
        assertThat(decodeCustomImageHosts(null)).isEmpty()
        assertThat(decodeCustomImageHosts("")).isEmpty()
        assertThat(decodeCustomImageHosts("\n\n")).isEmpty()
    }

    @Test
    fun decode_skipsBlankLines() {
        assertThat(decodeCustomImageHosts("a.com\n\nb.com\n")).isEqualTo(listOf("a.com", "b.com"))
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.datastore.CustomImageHostsSerializationTest"`
Expected: 编译失败（函数不存在）。

- [ ] **Step 3: 实现**

`Models.kt` 中 AppSettings 增加字段：

```kotlin
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pinnedHomeNode: PinnedHomeNode? = null,
    val customImageHosts: List<String> = emptyList(),
)
```

`SettingsStore.kt`：

1. `Keys` 增加 `val customImageHosts = stringPreferencesKey("custom_image_hosts")`。
2. `settings` Flow 的 `AppSettings(...)` 构造中增加
   `customImageHosts = decodeCustomImageHosts(preferences[Keys.customImageHosts])`。
3. 新增方法：

```kotlin
    suspend fun setCustomImageHosts(hosts: List<String>) {
        context.nodeFlowDataStore.edit { preferences ->
            if (hosts.isEmpty()) {
                preferences.remove(Keys.customImageHosts)
            } else {
                preferences[Keys.customImageHosts] = encodeCustomImageHosts(hosts)
            }
        }
    }
```

4. 文件末尾（类外）增加纯函数（换行分隔，与现有简单键值风格一致）：

```kotlin
/** 域名列表 ↔ DataStore 字符串的序列化，换行分隔。 */
internal fun encodeCustomImageHosts(hosts: List<String>): String = hosts.joinToString("\n")

internal fun decodeCustomImageHosts(raw: String?): List<String> =
    raw.orEmpty().split('\n').map(String::trim).filter(String::isNotEmpty)
```

- [ ] **Step 4: 运行验证通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.datastore.CustomImageHostsSerializationTest"`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt app/src/main/java/app/mystery0/nodeflow/core/datastore/SettingsStore.kt app/src/test/java/app/mystery0/nodeflow/core/datastore/CustomImageHostsSerializationTest.kt
git commit -m "feat: AppSettings 与 SettingsStore 支持自定义图床域名持久化"
```

---

### Task 3: Repository 与 UseCase 链路

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/settings/SettingsRepository.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/settings/SettingsRepositoryImpl.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/domain/settings/UpdateSettingsUseCase.kt`

**Interfaces:**
- Consumes: `SettingsStore.setCustomImageHosts(hosts: List<String>)`（Task 2）。
- Produces: `UpdateSettingsUseCase.setCustomImageHosts(hosts: List<String>)`（Task 7 依赖）。

纯透传无业务逻辑，不新增测试，编译即验证。无新 Koin 注册（均为既有类）。

- [ ] **Step 1: 三处各加一个方法**

`SettingsRepository.kt` 接口内（`setPinnedHomeNode` 之后）：

```kotlin
    suspend fun setCustomImageHosts(hosts: List<String>)
```

`SettingsRepositoryImpl.kt`（`setPinnedHomeNode` 实现之后）：

```kotlin
    override suspend fun setCustomImageHosts(hosts: List<String>) {
        settingsStore.setCustomImageHosts(hosts)
    }
```

`UpdateSettingsUseCase.kt`（`setPinnedHomeNode` 之后）：

```kotlin
    suspend fun setCustomImageHosts(hosts: List<String>) {
        repository.setCustomImageHosts(hosts)
    }
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/domain/settings/SettingsRepository.kt app/src/main/java/app/mystery0/nodeflow/data/settings/SettingsRepositoryImpl.kt app/src/main/java/app/mystery0/nodeflow/domain/settings/UpdateSettingsUseCase.kt
git commit -m "feat: 设置仓库与用例支持更新自定义图床域名"
```

---

### Task 4: CompositionLocal 与 HtmlText（回复/通知）接入

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/CustomImageHosts.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/HtmlText.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/HtmlTextTest.kt`（追加用例）

**Interfaces:**
- Consumes: `ImageHostMatcher.shouldLoadAsImage(url, hosts)`（Task 1）。
- Produces: `LocalCustomImageHosts: ProvidableCompositionLocal<Set<String>>`（Task 5/6 依赖）；`extractHtmlImageSpecs(html: String, customImageHosts: Collection<String> = emptySet()): List<HtmlImageSpec>`（新签名，旧调用不受影响）。

- [ ] **Step 1: 写失败测试（HtmlTextTest 追加）**

```kotlin
    @Test
    fun extractHtmlImageSpecs_treatsCustomHostAnchorAsImage() {
        val html = """<a href="https://img.example.com/abc">https://img.example.com/abc</a>"""

        val specs = extractHtmlImageSpecs(html, customImageHosts = setOf("example.com"))

        assertThat(specs).hasSize(1)
        assertThat(specs[0].url).isEqualTo("https://img.example.com/abc")
        assertThat(specs[0].compact).isFalse()
    }

    @Test
    fun extractHtmlImageSpecs_ignoresAnchorNotMatchingCustomHosts() {
        val html = """<a href="https://other.com/abc">link</a>"""

        assertThat(extractHtmlImageSpecs(html, customImageHosts = setOf("example.com"))).isEmpty()
    }

    @Test
    fun extractHtmlImageSpecs_withoutCustomHostsKeepsLegacyBehavior() {
        val html = """<a href="https://other.com/pic.png">pic</a>"""

        val specs = extractHtmlImageSpecs(html)

        assertThat(specs).hasSize(1)
        assertThat(specs[0].url).isEqualTo("https://other.com/pic.png")
    }
```

- [ ] **Step 2: 运行验证失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.HtmlTextTest"`
Expected: 编译失败（参数不存在）。

- [ ] **Step 3: 实现**

新文件 `CustomImageHosts.kt`：

```kotlin
package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * 用户配置的自定义图床域名集合，由应用根部从设置流提供；
 * HtmlText/RichHtmlText 用它把命中域名的链接按图片渲染。
 */
val LocalCustomImageHosts: ProvidableCompositionLocal<Set<String>> =
    compositionLocalOf { emptySet() }
```

`HtmlText.kt` 修改：

1. 增加 import：`import app.mystery0.nodeflow.core.link.ImageHostMatcher`。
2. `HtmlText` composable 中：

```kotlin
    val customImageHosts = LocalCustomImageHosts.current
    val contentHtml = remember(html) { htmlWithoutImages(linkifyV2exTopicReferences(html)) }
    val images = remember(html, customImageHosts) { extractHtmlImageSpecs(html, customImageHosts) }
```

3. `extractHtmlImageSpecs` 签名与 linkedImages 分支：

```kotlin
internal fun extractHtmlImageSpecs(
    html: String,
    customImageHosts: Collection<String> = emptySet(),
): List<HtmlImageSpec> {
```

linkedImages 的 mapNotNull 中，把

```kotlin
            val url = link.absUrl("href").takeIf { it.isImageUrl() } ?: return@mapNotNull null
```

改为

```kotlin
            val url = link.absUrl("href")
                .takeIf { it.isImageUrl() || ImageHostMatcher.shouldLoadAsImage(it, customImageHosts) }
                ?: return@mapNotNull null
```

- [ ] **Step 4: 运行验证通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.HtmlTextTest"`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/CustomImageHosts.kt app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/HtmlText.kt app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/HtmlTextTest.kt
git commit -m "feat: HtmlText 支持自定义图床域名链接按图片渲染"
```

---

### Task 5: buildV2exHtmlDocument（正文 WebView）接入

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/V2exHtmlDocument.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/RichHtmlText.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/V2exHtmlDocumentTest.kt`（追加用例）

**Interfaces:**
- Consumes: `ImageHostMatcher.shouldLoadAsImage(url, hosts)`（Task 1）、`LocalCustomImageHosts`（Task 4）。
- Produces: `buildV2exHtmlDocument(bodyHtml, colors, customImageHosts: Collection<String> = emptySet())`（新签名，旧调用不受影响）。

- [ ] **Step 1: 写失败测试（V2exHtmlDocumentTest 追加；colors 复用文件内既有字面量写法）**

```kotlin
    @Test
    fun buildV2exHtmlDocument_insertsImgAfterCustomHostAnchor() {
        val document = buildV2exHtmlDocument(
            bodyHtml = """<p><a href="https://img.example.com/abc">https://img.example.com/abc</a></p>""",
            colors = V2exHtmlColors(
                text = "#111111",
                secondaryText = "#666666",
                link = "#0066cc",
                background = "#ffffff",
                codeBackground = "#f3f4f6",
                quoteBackground = "#f7f8fa",
                border = "#dddddd",
            ),
            customImageHosts = setOf("example.com"),
        )

        // 锚点保留可点击，其后插入同 URL 的 <img> 交给现有脚本管理
        assertThat(document).contains(""">https://img.example.com/abc</a>""")
        assertThat(document).contains("""<img src="https://img.example.com/abc"""")
    }

    @Test
    fun buildV2exHtmlDocument_withoutCustomHostsDoesNotInsertImg() {
        val document = buildV2exHtmlDocument(
            bodyHtml = """<p><a href="https://img.example.com/abc">link</a></p>""",
            colors = V2exHtmlColors(
                text = "#111111",
                secondaryText = "#666666",
                link = "#0066cc",
                background = "#ffffff",
                codeBackground = "#f3f4f6",
                quoteBackground = "#f7f8fa",
                border = "#dddddd",
            ),
        )

        assertThat(document).doesNotContain("<img")
    }
```

- [ ] **Step 2: 运行验证失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.V2exHtmlDocumentTest"`
Expected: 编译失败（参数不存在）。

- [ ] **Step 3: 实现**

`V2exHtmlDocument.kt`：

1. import：`import app.mystery0.nodeflow.core.link.ImageHostMatcher` 与 `import org.jsoup.nodes.Element`。
2. 签名：

```kotlin
internal fun buildV2exHtmlDocument(
    bodyHtml: String,
    colors: V2exHtmlColors,
    customImageHosts: Collection<String> = emptySet(),
): String {
```

3. 在 `body().linkifyPlainV2exTopicLinks()` 之后、`select("a[href]")` 循环之前插入
   （顺序保证新 img 也吃到后续 eager/decoding 属性）：

```kotlin
        // 命中自定义图床域名的锚点后插入同 URL 的 <img>，
        // 由注入脚本接管占位、失败重试与点击预览；锚点保留可点击
        select("a[href]")
            .filter { link ->
                link.select("img").isEmpty() &&
                    ImageHostMatcher.shouldLoadAsImage(link.absUrl("href"), customImageHosts)
            }
            .forEach { link ->
                link.after(Element("img").attr("src", link.absUrl("href")))
            }
```

`RichHtmlText.kt`：

```kotlin
    val customImageHosts = LocalCustomImageHosts.current
    val htmlDocument = remember(html, colorScheme, customImageHosts) {
        buildV2exHtmlDocument(
            bodyHtml = html,
            colors = V2exHtmlColors(
                // ...原有参数不变...
            ),
            customImageHosts = customImageHosts,
        )
    }
```

- [ ] **Step 4: 运行验证通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.V2exHtmlDocumentTest"`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/V2exHtmlDocument.kt app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/RichHtmlText.kt app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/V2exHtmlDocumentTest.kt
git commit -m "feat: 主题正文 WebView 支持自定义图床域名链接按图片渲染"
```

---

### Task 6: 应用根部提供 LocalCustomImageHosts

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/MainActivity.kt`（setContent 块，约 25-35 行）

**Interfaces:**
- Consumes: `LocalCustomImageHosts`（Task 4）、`AppSettings.customImageHosts`（Task 2）。

- [ ] **Step 1: 实现**

import 增加：

```kotlin
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import app.mystery0.nodeflow.core.designsystem.component.LocalCustomImageHosts
```

setContent 中包一层：

```kotlin
        setContent {
            val appViewModel: AppViewModel = koinViewModel()
            val settings = appViewModel.settings.collectAsStateWithLifecycle()
            val customImageHosts = remember(settings.value.customImageHosts) {
                settings.value.customImageHosts.toSet()
            }
            NodeFlowTheme(settings = settings.value) {
                CompositionLocalProvider(LocalCustomImageHosts provides customImageHosts) {
                    NodeFlowNavHost(
                        settings = settings.value,
                        deepLink = pendingDeepLink,
                        onDeepLinkConsumed = { pendingDeepLink = null },
                    )
                }
            }
        }
```

- [ ] **Step 2: 编译验证**

Run: `.\gradlew.bat :app:assembleDebug`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/MainActivity.kt
git commit -m "feat: 应用根部注入自定义图床域名配置"
```

---

### Task 7: 设置界面域名管理

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/feature/settings/CustomImageHostsLogic.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsUiEvent.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsViewModel.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsScreen.kt`
- Test: `app/src/test/java/app/mystery0/nodeflow/feature/settings/CustomImageHostsLogicTest.kt`

**Interfaces:**
- Consumes: `ImageHostMatcher.normalizeHost(input)`（Task 1）、`UpdateSettingsUseCase.setCustomImageHosts(hosts)`（Task 3）。
- Produces: `addCustomImageHost(current: List<String>, input: String): AddImageHostResult`；UI 事件 `AddCustomImageHost(input)` / `RemoveCustomImageHost(host)`。

- [ ] **Step 1: 写失败测试**

```kotlin
package app.mystery0.nodeflow.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CustomImageHostsLogicTest {
    @Test
    fun add_normalizesAndAppends() {
        val result = addCustomImageHost(listOf("a.com"), "https://Img.Example.com/x")
        assertThat(result).isEqualTo(
            AddImageHostResult.Added(listOf("a.com", "img.example.com")),
        )
    }

    @Test
    fun add_rejectsInvalidInput() {
        assertThat(addCustomImageHost(emptyList(), "   ")).isEqualTo(AddImageHostResult.Invalid)
        assertThat(addCustomImageHost(emptyList(), "bad host")).isEqualTo(AddImageHostResult.Invalid)
    }

    @Test
    fun add_rejectsDuplicateAfterNormalization() {
        assertThat(addCustomImageHost(listOf("example.com"), "HTTPS://EXAMPLE.COM/"))
            .isEqualTo(AddImageHostResult.Duplicate)
    }
}
```

- [ ] **Step 2: 运行验证失败**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.settings.CustomImageHostsLogicTest"`
Expected: 编译失败。

- [ ] **Step 3: 实现**

新文件 `CustomImageHostsLogic.kt`：

```kotlin
package app.mystery0.nodeflow.feature.settings

import app.mystery0.nodeflow.core.link.ImageHostMatcher

/** 添加自定义图床域名的结果。 */
sealed interface AddImageHostResult {
    data class Added(val hosts: List<String>) : AddImageHostResult
    data object Invalid : AddImageHostResult
    data object Duplicate : AddImageHostResult
}

/** 归一化输入并追加到列表；非法输入或归一化后重复分别返回对应结果。 */
fun addCustomImageHost(current: List<String>, input: String): AddImageHostResult {
    val host = ImageHostMatcher.normalizeHost(input) ?: return AddImageHostResult.Invalid
    if (host in current) return AddImageHostResult.Duplicate
    return AddImageHostResult.Added(current + host)
}
```

`SettingsUiEvent.kt` 增加：

```kotlin
    data class AddCustomImageHost(val input: String) : SettingsUiEvent
    data class RemoveCustomImageHost(val host: String) : SettingsUiEvent
```

`SettingsViewModel.kt` 的 `onEvent` when 增加分支：

```kotlin
            is SettingsUiEvent.AddCustomImageHost -> viewModelScope.launch {
                when (val result = addCustomImageHost(
                    current = _uiState.value.settings.customImageHosts,
                    input = event.input,
                )) {
                    is AddImageHostResult.Added ->
                        updateSettings.setCustomImageHosts(result.hosts)
                    AddImageHostResult.Invalid ->
                        _uiState.update { it.copy(message = "无效的图床域名") }
                    AddImageHostResult.Duplicate ->
                        _uiState.update { it.copy(message = "该域名已存在") }
                }
            }
            is SettingsUiEvent.RemoveCustomImageHost -> viewModelScope.launch {
                updateSettings.setCustomImageHosts(
                    _uiState.value.settings.customImageHosts - event.host,
                )
            }
```

`SettingsScreen.kt`：在「数据」分组之前增加「内容浏览」分组（`SettingsSectionTitle("内容浏览")`），
其下：

```kotlin
            HorizontalDivider()
            SettingsSectionTitle("内容浏览")
            var showAddImageHostDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("自定义图床域名") },
                supportingContent = {
                    Text("命中这些域名的链接将尝试按图片加载")
                },
                trailingContent = {
                    Button(onClick = { showAddImageHostDialog = true }) {
                        Icon(Icons.Outlined.Add, contentDescription = null)
                        Text("添加")
                    }
                },
            )
            state.settings.customImageHosts.forEach { host ->
                ListItem(
                    headlineContent = { Text(host) },
                    trailingContent = {
                        IconButton(
                            onClick = { onEvent(SettingsUiEvent.RemoveCustomImageHost(host)) },
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除 $host")
                        }
                    },
                )
            }
            if (showAddImageHostDialog) {
                AddImageHostDialog(
                    onConfirm = { input ->
                        onEvent(SettingsUiEvent.AddCustomImageHost(input))
                        showAddImageHostDialog = false
                    },
                    onDismiss = { showAddImageHostDialog = false },
                )
            }
```

文件底部新增私有对话框组件：

```kotlin
@Composable
private fun AddImageHostDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加图床域名") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("输入域名（如 img.example.com），将同时匹配其子域名。")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    placeholder = { Text("example.com") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = input.isNotBlank(),
                onClick = { onConfirm(input) },
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
```

所需新增 import（`SettingsScreen.kt`）：

```kotlin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
```

- [ ] **Step 4: 运行验证通过**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.settings.CustomImageHostsLogicTest"`
Expected: BUILD SUCCESSFUL。

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/mystery0/nodeflow/feature/settings/CustomImageHostsLogic.kt app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsUiEvent.kt app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsViewModel.kt app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsScreen.kt app/src/test/java/app/mystery0/nodeflow/feature/settings/CustomImageHostsLogicTest.kt
git commit -m "feat: 设置界面支持管理自定义图床域名"
```

---

### Task 8: 全量验证、真机检查与文档

**Files:**
- Modify: `docs/subsystems/content-rendering.md`
- Modify: `docs/subsystems/storage.md`
- Modify: `docs/index.md`

- [ ] **Step 1: 全量测试与构建**

Run: `.\gradlew.bat :app:testDebugUnitTest` 然后 `.\gradlew.bat :app:assembleDebug`
Expected: 均 BUILD SUCCESSFUL。

- [ ] **Step 2: 模拟器验证（按设计文档验证清单）**

1. `.\gradlew.bat :app:installDebug`，打开设置 → 添加域名 `imgur.com`。
2. 打开帖子 1226866 等页面确认无回归；打开示例帖
   `adb shell am start -a android.intent.action.VIEW -d "https://www.v2ex.com/t/1227803" app.mystery0.nodeflow`：
   正文链接下出现图片位（相册页预期显示加载失败占位），链接仍可点。
3. 添加一个真实直链域名（如 `i.imgur.com` 并找带直链的帖子，或自搭 URL 验证 Coil 路径），确认加载成功与点击大图预览。
4. 设置中删除域名后返回帖子，恢复为普通链接。
5. 输入非法域名（如 `bad host`）确认 Snackbar 提示；重复添加确认提示。

- [ ] **Step 3: 文档更新**

`content-rendering.md`「富文本与链接」增加：

```markdown
- 用户可在设置中配置自定义图床域名（`ImageHostMatcher` + `LocalCustomImageHosts`）；
  命中域名的链接在回复（`HtmlText`）与正文（`RichHtmlText`）中按图片尝试加载，
  失败回退现有占位，原链接保留可点击。
```

`storage.md` DataStore 相关小节增加一行：

```markdown
- 自定义图床域名列表存于 `custom_image_hosts`（换行分隔字符串），经 `AppSettings.customImageHosts` 暴露。
```

`docs/index.md` 设计条目补充实施计划链接：

```markdown
- [自定义图床域名设计](plans/2026-07-17-custom-image-host-design.md) / [实施计划](plans/2026-07-17-custom-image-host.md)
```

- [ ] **Step 4: Commit**

```bash
git add docs/subsystems/content-rendering.md docs/subsystems/storage.md docs/index.md
git commit -m "docs: 补充自定义图床域名子系统与索引说明"
```
