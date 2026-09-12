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
| Gradle、依赖或构建配置 | `:app:testDebugUnitTest`、`:app:assembleDebug`；涉及 Lint 配置或 Android 兼容性时执行 `:app:lintDebug` |
| Room Entity/DAO/schema | 数据库测试、全量 JVM 测试、Debug 构建及 schema 检查；版本升级还需 Migration 测试 |
| 网络、Cookie、登录、访问控制、HTML 解析 | 相关 MockWebServer/Parser 回归测试、全量 JVM 测试、Debug 构建 |
| Release、混淆、签名 | 相关检查及安全配置可用时的 Release 构建；运行受混淆影响的关键流程 |

## 测试实现

JVM 测试位于 `app/src/test/`，重点覆盖 UseCase、Repository、数据源、Parser、链接与格式化、ViewModel 状态转换、缓存策略和网络异常映射。网络测试使用 MockWebServer 或测试替身，不访问真实 V2EX；HTML fixture 最小化且脱敏。协程和 Flow 使用 `kotlinx-coroutines-test`，断言优先使用 Truth。

需要真实 Room 数据库的 DAO 与 Migration 测试应放入 `app/src/androidTest/`，不能以 JVM 全量测试替代。当前仓库已有设备 UI 测试和使用 Fake DAO 的 JVM 测试，尚无真实 Room DAO/Migration 测试；数据库变更时应补齐对应覆盖。使用已连接的设备或模拟器运行 `:app:connectedDebugAndroidTest`，需要时限定到相关测试类。当前版本 1 开发基线的历史见[存储说明](../subsystems/storage.md)；版本升级应验证已有数据能够保留。

## 真机或模拟器验证

只验证本次受影响的流程，典型范围如下：

- UI：Dynamic Color、深色模式、edge-to-edge、系统栏和字体缩放。
- 导航：返回栈、外部深链和浏览器回退。
- 会话：登录页面、Cookie 持久化和受限页面处理。
- 内容：图片加载、SVG、正文图片与大图缩放。
- 恢复：网络切换、离线缓存和进程重启。

缺少设备、账户或环境时，完成可执行检查，并在交付中说明未验证流程、原因和剩余风险；不声称仅凭 JVM 测试确认了设备行为。
