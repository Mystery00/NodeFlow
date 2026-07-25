# 系统深浅色启动页实施计划

> **执行要求：** 按本计划逐项完成，遵循 TDD 的 RED → GREEN → REFACTOR 顺序。

**目标：** 使用 AndroidX SplashScreen，让应用冷启动页背景跟随系统深浅色模式。

**架构：** `MainActivity` 使用独立的 `Theme.NodeFlow.Starting`，系统启动完成后通过
`postSplashScreenTheme` 切换回现有 `Theme.NodeFlow`。日间和夜间资源目录提供同名背景色，
Compose 主题与应用设置逻辑保持不变。

**技术栈：** Kotlin、AndroidX Core SplashScreen 1.2.0、Android resources、AndroidJUnit4、Truth。

## 全局约束

- 最低 SDK 29，Compile/Target SDK 37，JVM target 21。
- 仅跟随系统深浅色，不读取 App 内主题设置。
- 不新增启动 Activity、自定义动画或启动等待条件。
- 不修改现有应用图标与 Compose `NodeFlowTheme`。
- 未经用户要求不提交、不推送 Git。

---

### 任务 1：用 instrumentation 测试复现启动主题问题

**文件：**

- 新建：`app/src/androidTest/java/app/mystery0/nodeflow/MainActivitySplashThemeTest.kt`

**接口：**

- 使用 PackageManager 读取 `MainActivity` 在 Manifest 中声明的主题。
- 使用带 `UI_MODE_NIGHT_NO` 或 `UI_MODE_NIGHT_YES` 的 `Configuration` 解析同名颜色资源。

- [ ] **步骤 1：编写失败测试**

```kotlin
package app.mystery0.nodeflow

import android.content.ComponentName
import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySplashThemeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun mainActivity_usesStartingTheme() {
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )

        assertThat(context.resources.getResourceEntryName(activityInfo.theme))
            .isEqualTo("Theme.NodeFlow.Starting")
    }

    @Test
    fun splashBackground_followsSystemNightMode() {
        assertThat(resolveSplashBackground(Configuration.UI_MODE_NIGHT_NO))
            .isEqualTo(0xFFFFFBFE.toInt())
        assertThat(resolveSplashBackground(Configuration.UI_MODE_NIGHT_YES))
            .isEqualTo(0xFF1C1B1F.toInt())
    }

    @Test
    fun splashIcon_usesForegroundAndExplicitBackground() {
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )
        val themedContext = ContextThemeWrapper(context, activityInfo.theme)
        val attributes = themedContext.obtainStyledAttributes(
            intArrayOf(
                androidx.core.splashscreen.R.attr.windowSplashScreenAnimatedIcon,
                androidx.core.splashscreen.R.attr.windowSplashScreenIconBackgroundColor,
            ),
        )

        try {
            assertThat(attributes.getResourceId(0, 0))
                .isEqualTo(R.drawable.ic_launcher_foreground)
            assertThat(attributes.getColor(1, 0))
                .isEqualTo(0xFF202124.toInt())
        } finally {
            attributes.recycle()
        }
    }

    private fun resolveSplashBackground(nightMode: Int): Int {
        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
        }
        val resources = context.createConfigurationContext(configuration).resources
        val colorId = resources.getIdentifier(
            "splash_screen_background",
            "color",
            context.packageName,
        )
        assertThat(colorId).isNotEqualTo(0)
        return resources.getColor(colorId, null)
    }
}
```

- [ ] **步骤 2：在模拟器运行测试并确认 RED**

运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=app.mystery0.nodeflow.MainActivitySplashThemeTest
```

预期：测试失败，现有 Activity 主题为 `Theme.NodeFlow`，且启动背景资源不存在。

### 任务 2：接入 AndroidX SplashScreen 并完成 GREEN

**文件：**

- 修改：`gradle/libs.versions.toml`
- 修改：`app/build.gradle.kts`
- 新建：`app/src/main/res/values/colors.xml`
- 新建：`app/src/main/res/values-night/colors.xml`
- 修改：`app/src/main/res/values/themes.xml`
- 修改：`app/src/main/AndroidManifest.xml`
- 修改：`app/src/main/java/app/mystery0/nodeflow/MainActivity.kt`

**接口：**

- `Theme.NodeFlow.Starting` 由 Manifest 供 `MainActivity` 使用。
- `installSplashScreen()` 必须在 `super.onCreate()` 之前调用。
- `postSplashScreenTheme` 切回 `Theme.NodeFlow`。

- [ ] **步骤 1：添加版本目录与模块依赖**

在 `gradle/libs.versions.toml` 增加：

```toml
splashscreen = "1.2.0"
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "splashscreen" }
```

在 `app/build.gradle.kts` 增加：

```kotlin
implementation(libs.androidx.core.splashscreen)
```

- [ ] **步骤 2：添加日间与夜间启动背景色**

`values/colors.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="splash_screen_background">#FFFBFE</color>
</resources>
```

`values-night/colors.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="splash_screen_background">#1C1B1F</color>
</resources>
```

- [ ] **步骤 3：添加启动主题并绑定 MainActivity**

在 `themes.xml` 增加：

```xml
<style name="Theme.NodeFlow.Starting" parent="Theme.SplashScreen.IconBackground">
    <item name="windowSplashScreenAnimatedIcon">@drawable/ic_launcher_foreground</item>
    <item name="windowSplashScreenIconBackgroundColor">@color/ic_launcher_background</item>
    <item name="windowSplashScreenBackground">@color/splash_screen_background</item>
    <item name="postSplashScreenTheme">@style/Theme.NodeFlow</item>
</style>
```

将 Manifest 中 `MainActivity` 的主题改为：

```xml
android:theme="@style/Theme.NodeFlow.Starting"
```

- [ ] **步骤 4：安装 SplashScreen**

在 `MainActivity.kt` 导入并调用：

```kotlin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    // 保留现有初始化
}
```

- [ ] **步骤 5：重新运行局部 instrumentation 测试并确认 GREEN**

运行任务 1 的同一命令。

预期：三个测试均通过。

### 任务 3：回归与模拟器冷启动验证

**文件：**

- 修改：`docs/index.md`

- [ ] **步骤 1：运行 JVM 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 2：构建 Debug APK**

```powershell
.\gradlew.bat :app:assembleDebug
```

预期：BUILD SUCCESSFUL。

- [ ] **步骤 3：在模拟器验证浅色冷启动**

安装 Debug APK，将系统夜间模式设为 `no`，强制停止应用后从 Launcher 启动。确认启动背景为浅色、
图标正常、进入首页时无异常闪屏。

- [ ] **步骤 4：在模拟器验证深色冷启动**

将系统夜间模式设为 `yes`，强制停止应用后从 Launcher 启动。确认启动背景为深色、图标正常、
进入首页时不再闪白。

- [ ] **步骤 5：检查工作区**

运行：

```powershell
git diff --check
git status --short
```

预期：无空白错误，仅包含本任务相关文件。
