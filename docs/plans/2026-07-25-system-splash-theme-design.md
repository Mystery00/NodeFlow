# 系统深浅色启动页设计

## 背景

`MainActivity` 当前直接使用继承自 `android:Theme.Material.Light.NoActionBar` 的
`Theme.NodeFlow`。应用冷启动时，系统先根据 Activity 的平台主题绘制启动窗口，
Compose 中的 `NodeFlowTheme` 要在 `setContent` 之后才会生效，因此系统处于深色模式时，
启动页仍会短暂显示白色背景。

## 目标

- 使用 AndroidX SplashScreen API 统一 Android 版本之间的启动页行为。
- 启动页背景跟随系统深浅色模式。
- 保留现有应用图标，不增加自定义启动动画或额外启动 Activity。
- SplashScreen 退出后继续使用现有 `Theme.NodeFlow` 和 Compose 主题逻辑。
- 不读取 App 内的主题模式设置；本次只跟随系统配置。

## 方案

### 依赖与主题

在版本目录中加入稳定版 `androidx.core:core-splashscreen:1.2.0`，并由 `app` 模块引用。

新增 `Theme.NodeFlow.Starting`，继承 `Theme.SplashScreen.IconBackground`：

- `windowSplashScreenAnimatedIcon` 使用现有 `@drawable/ic_launcher_foreground`。
- `windowSplashScreenIconBackgroundColor` 使用现有 `@color/ic_launcher_background`，避免系统深色模式下
  选择 adaptive icon 的低对比度单色层。
- `windowSplashScreenBackground` 使用专用的 `splash_screen_background` 颜色资源。
- `postSplashScreenTheme` 指回现有 `Theme.NodeFlow`。

`MainActivity` 在 Manifest 中使用 `Theme.NodeFlow.Starting`；Application 和
`CrashActivity` 仍使用 `Theme.NodeFlow`。

### 深浅色资源

分别在 `values/colors.xml` 与 `values-night/colors.xml` 定义同名
`splash_screen_background`：

- 浅色：`#FFFBFE`，与关闭 Dynamic Color 时 Material 3 默认浅色背景一致。
- 深色：`#1C1B1F`，与关闭 Dynamic Color 时 Material 3 默认深色背景一致。

Dynamic Color 的实际背景由壁纸配色生成，启动阶段不读取 Compose 或 DataStore 状态，
因此本次只保证深浅色语义和默认背景接近，不保证与每一套动态配色逐像素一致。

### Activity 接入

`MainActivity.onCreate` 在 `super.onCreate` 之前调用 `installSplashScreen()`。不设置
keep-on-screen 条件与退出动画，避免延长启动时间。

## 验证

- 增加 Android instrumentation 测试，分别以日间与夜间 `Configuration` 解析
  `splash_screen_background`，验证夜间颜色为深色且与日间颜色不同。
- 运行 `:app:testDebugUnitTest`，确认现有 JVM 测试无回归。
- 运行 `:app:assembleDebug`，验证 Manifest、主题、资源和依赖均可正确合并。
- 如有可用模拟器或真机，分别在系统浅色和深色模式下冷启动应用，检查启动背景、应用图标、
  SplashScreen 到 Compose 首页的过渡以及系统栏是否闪白。

## 非目标

- 不让启动页跟随 App 内强制浅色或强制深色设置。
- 不自定义图标动画、退出动画或启动时长。
- 不修改 Compose `NodeFlowTheme`、Dynamic Color 或应用图标资源。
