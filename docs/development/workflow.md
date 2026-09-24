# 开发工作流

项目边界和自主执行约定见 [AGENTS.md](../../AGENTS.md)，验证范围统一由[测试与验证](testing.md)维护。

## 从任务到交付

1. 检查工作区差异，读取相关源码与必要文档，确定目标行为和验收方式。修复问题时用现有测试、最小 fixture 或可复现步骤确认原因。
2. 在现有架构内完成任务；常规实现选择自行处理。复杂改动保留简短计划，只为需要长期保存的设计取舍创建文档，不为每个小改动生成设计、计划和审批链。
3. 补充或更新能检验行为的测试，按验证矩阵执行；验证通过且没有新增风险时停止扩大检查。仅在行为、接口或开发方式变化时同步受影响文档。
4. 检查最终 diff 的正确性和任务范围，报告实际结果、已执行检查及未完成验证的原因。新增依赖检查 Koin 注册，数据库改动检查 schema 与迁移。

## 编码与隐私

- 遵循现有 Kotlin 风格，优先不可变数据与结构化并发；避免 `!!`、吞掉协程取消、无差别捕获异常和无关重构。
- 通用组件进入 `core` 前应有明确复用场景；UI 文案优先使用 string resource。
- 日志用简短英文事件与键值对表达。不在日志、异常、测试输出、截图、文档或提交中暴露 Cookie、Token、密码、once、Authorization header 或用户隐私内容。
- 可查看或分享的崩溃报告只保留异常类型与代码位置，不输出异常 message。不提交真实账户数据、未脱敏 HTML、签名文件、`local.properties` 或私有密钥。
- 新增敏感数据时明确存储位置、生命周期、清理方式和备份规则；不新增与核心功能无关的埋点、统计上传或远程日志。

## 工具与执行

使用 `rg` 定位文件和符号，限制读取范围；独立查询批量执行，有数据依赖或共享写入的操作串行。并行子任务需要明确输入、输出和文件归属，避免重复检索与同时修改同一文件。

PowerShell 在仓库根目录运行以下命令，按验证矩阵选用，不要求每次全部执行：

```powershell
# 局部测试：将占位符替换为实际测试类名或匹配模式
.\gradlew.bat :app:testDebugUnitTest --tests '<测试类完整名称或匹配模式>'
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
# 需要已连接的模拟器或设备
.\gradlew.bat :app:connectedDebugAndroidTest
```

同一工作目录的 Gradle 任务串行或在一次调用中执行，避免争用构建输出。环境缺失时先诊断并在授权范围内修复；确实无法运行则交付已完成部分和阻塞原因，不把未运行描述为通过。

Release 验证使用 `.\gradlew.bat :app:assembleRelease`，需要本地签名配置。`versionCode` 来自 Git 提交数量，`versionName` 来自 `gradle/libs.versions.toml`；Debug/Release 追加构建类型、提交数和 Git SHA 后缀。

## 主分支 CI 与 Release 验证

`.github/workflows/android_master.yml` 在 main 推送、面向 main 的 PR 和手动运行时执行验证。main 推送仍支持提交消息中的 `ci skip`；只有 main 上成功的非 PR 运行才创建 `pre-*` 预发布。手动运行其他分支只验证，不发布。

非 PR 构建使用现有签名 Secrets：`KEYSTORE_BASE64`（兼容 `SIGN_KEY_STORE_BASE64`）、`SIGN_KEY_STORE_PASSWORD`（兼容 `KEYSTORE_PASSWORD`）、`SIGN_KEY_ALIAS`（兼容 `KEY_ALIAS`）和 `SIGN_KEY_PASSWORD`（兼容 `KEY_PASSWORD`）。缺失配置立即失败，不回退到 debug 签名。PR 使用一次性测试密钥，产物只供 CI 验证，不能替代正式签名的安装包。keystore 验证后立即删除，不上传到 Artifact。

验证依次包含脚本替身测试、全量 Debug JVM 测试、Debug 构建、Release Lint、启用 R8 和资源压缩的 Release 构建，以及最终 APK 的证书、应用包名和非 debuggable 标记检查。只接受 release 元数据明确指向的单个 APK；缺少 `mapping.txt`、未签名或证书与配置不符时禁止发布。

模拟器直接安装最终的同一个 Release APK，在 API 29（当前 minSdk）上关闭 Wi-Fi 和移动数据，检查首次启动、保留数据冷启动及帖子、节点、用户离线深链。检查包括主进程持续存活、MainActivity 位于前台和没有进入独立的 CrashActivity 进程。此检查没有测试代码参与 APK 混淆，但不覆盖在线 JSON 解析、已登录会话恢复、真实 Room 数据迁移、图片解码或后台通知实际执行；这些流程仍需按[测试与验证](testing.md)在 Release 包上验证，不能用 Debug JVM 测试替代。

通过验证后上传 `NodeFlow-release-<versionName>.apk` 和对应 `NodeFlow-<versionName>-mapping.txt`；预发布附件保留该构建对应的 mapping，完整 R8 报告作为 Actions Artifact 保留 90 天。签名不同的旧 Debug 包不能覆盖安装，卸载前应先处理需要保留的本地数据。

仅验证 CI 脚本时，在提供 Bash、Python 3 和 jq 的环境执行 `python3 -m unittest discover -s scripts/ci -p 'test_*.py' -v`。这些测试使用 SDK/ADB 替身，不代表 Android 构建或设备验证通过。`smoke-release-apk.sh` 会关闭设备网络，仅用于一次性 CI 模拟器，不应直接对日常使用的设备运行。

## Git

提交、推送或创建 PR 仅在用户要求时执行；历史计划或技能中的提交步骤不构成授权。提交信息使用英文小写 Conventional Commits 类型和中文描述，例如 `fix: 修复受限页面识别错误`。
