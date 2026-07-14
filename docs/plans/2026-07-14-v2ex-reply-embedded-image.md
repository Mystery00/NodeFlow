# V2EX 回复大图分类修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让紧邻回复文字的 V2EX `embedded_image` 按正文大图显示并支持点击预览，同时保留普通内联小图行为。

**Architecture:** 仅调整 `HtmlText` 从回复 HTML 提取 `HtmlImageSpec` 时的分类优先级。图片自身的小图证据继续优先；服务端明确的 `embedded_image` 类只覆盖“相邻文字”这一弱启发式，图片加载、布局、重试与预览链路保持不变。

**Tech Stack:** Kotlin、Jetpack Compose、Jsoup、Coil、JUnit 4、Google Truth、Android Emulator

## Global Constraints

- 代码注释和文档使用中文，日志打印使用英文。
- 不修改图片网络请求、缓存、重试或预览器实现。
- 不改变主题正文 WebView 的图片逻辑。
- 不扩大支持新的图片 URL 后缀或第三方图床。
- 不修改或提交用户文件 `docs/2026-07-13-v2ex-daily-check-in-investigation.md`。

---

### Task 1: 修复回复大图分类并完成实机回归

**Files:**
- Modify: `app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/HtmlTextTest.kt:19-47`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/HtmlText.kt:204-230, 275-287, 327-335`

**Interfaces:**
- Consumes: `extractHtmlImageSpecs(html: String): List<HtmlImageSpec>`、`HtmlImageSpec.compact: Boolean`
- Produces: `Element.isExplicitContentImage(): Boolean`；`embedded_image` 紧邻文字时返回 `compact=false`

- [ ] **Step 1: 写入真实回复 HTML 的失败测试，并保留普通内联图用例**

将现有内联图测试中的 `class="embedded_image"` 删除，使其继续证明未知普通内联图为小图；随后新增真实回归测试：

```kotlin
@Test
fun extractHtmlImageSpecs_marksUnknownInlineImageAsCompact() {
    val html = """
        问了我 3W<a href="https://i.imgur.com/N9E3iZ2.png">
            <img src="https://i.imgur.com/N9E3iZ2.png" rel="noreferrer">
        </a>。这标准还没上实木
    """.trimIndent()

    val images = extractHtmlImageSpecs(html)

    assertThat(images).hasSize(1)
    assertThat(images.single().url).isEqualTo("https://i.imgur.com/N9E3iZ2.png")
    assertThat(images.single().compact).isTrue()
}

@Test
fun extractHtmlImageSpecs_keepsEmbeddedImageAfterReplyTextAsContentImage() {
    val html = """
        @<a href="/member/qbqbqbqb">qbqbqbqb</a> #8 因为他们认为 DNS 泄露不是问题
        <a href="https://i.imgur.com/HhIXhII.png" rel="nofollow noopener">
            <img src="https://i.imgur.com/HhIXhII.png" class="embedded_image" rel="noreferrer">
        </a>
    """.trimIndent()

    val images = extractHtmlImageSpecs(html)

    assertThat(images).hasSize(1)
    assertThat(images.single().url).isEqualTo("https://i.imgur.com/HhIXhII.png")
    assertThat(images.single().compact).isFalse()
}
```

- [ ] **Step 2: 运行单个回归测试并确认按预期失败**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.HtmlTextTest.extractHtmlImageSpecs_keepsEmbeddedImageAfterReplyTextAsContentImage"
```

Expected: FAIL，实际 `compact` 为 `true`，而测试期望 `false`。

- [ ] **Step 3: 实现最小分类修复**

在图片规格提取中拆开强小图证据与位置启发式：

```kotlin
val compactByImage = image.isCompactImage(width, height)
val compactByPlacement = image.isInlineImage() && !image.isExplicitContentImage()
HtmlImageSpec(
    url = url,
    alt = image.attr("alt").takeIf { it.isNotBlank() },
    widthPx = width,
    heightPx = height,
    compact = compactByImage || compactByPlacement,
)
```

增加只识别 V2EX 明确类名的辅助函数和常量：

```kotlin
private fun Element.isExplicitContentImage(): Boolean =
    classNames().any { className ->
        className.equals(V2EX_EMBEDDED_IMAGE_CLASS, ignoreCase = true)
    }

private const val V2EX_EMBEDDED_IMAGE_CLASS = "embedded_image"
```

- [ ] **Step 4: 运行 `HtmlTextTest` 并确认通过**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.HtmlTextTest"
```

Expected: BUILD SUCCESSFUL，`HtmlTextTest` 全部通过。

- [ ] **Step 5: 运行设计系统相关测试和全量单元测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.designsystem.component.*"
.\gradlew.bat :app:testDebugUnitTest
```

Expected: 两条命令均 BUILD SUCCESSFUL，无失败测试。

- [ ] **Step 6: 构建、安装并验证真实帖子**

Run:

```powershell
.\gradlew.bat :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop app.mystery0.nodeflow
adb shell am start -W -n app.mystery0.nodeflow/.MainActivity -a android.intent.action.VIEW -d "https://www.v2ex.com/t/1227054"
```

在模拟器滚动到第 10 楼，确认：

1. `https://i.imgur.com/HhIXhII.png` 不再缩成约 `56×35dp`，而是按回复可用宽度及 `955:588` 比例显示。
2. 点击图片后 UI 树出现“关闭大图”，证明进入 `ZoomableImageViewer`。
3. 大图预览不显示“图片加载失败”。

- [ ] **Step 7: 检查并提交代码**

Run:

```powershell
git diff --check
git status --short
git add -- app/src/main/java/app/mystery0/nodeflow/core/designsystem/component/HtmlText.kt app/src/test/java/app/mystery0/nodeflow/core/designsystem/component/HtmlTextTest.kt
git commit -m "修复：正确展示回复内嵌大图"
```

Expected: 只提交实现与测试；已单独提交的计划不重复变更，用户未跟踪文档保持未暂存。
