# 开发工作流

## 编码原则

- 遵循 Kotlin 官方编码规范和现有项目风格。
- 优先使用不可变数据、data class、sealed interface/class、扩展函数和结构化并发。
- 避免 `!!`、无差别捕获异常、吞掉协程取消和无关重构。
- 通用组件进入 `core` 前必须有明确的跨功能复用场景。
- UI 文案优先使用 string resource；代码注释、KDoc 和文档使用中文。
- 日志使用英文，以简短事件和键值对表达，不使用无意义的 `println`。

## 隐私与安全

- 不在日志、异常、测试输出、截图、文档或提交中暴露 Cookie、Token、密码、once、Authorization header 和用户隐私内容；可查看或分享的崩溃报告只保留异常类型与代码位置，不输出异常 message。
- 不提交真实账户数据、未脱敏 HTML、签名文件、`local.properties` 或私有密钥。
- 新增敏感数据时明确其存储位置、生命周期、清理方式和备份规则。
- 不新增与核心功能无关的埋点、统计上传或远程日志。

## 实施步骤

1. 从 `AGENTS.md` 的任务路由找到对应专题文档和历史计划。
2. 阅读相关源码、测试、构建配置和近期变更，确认真实行为。
3. 修复问题时先建立最小复现；业务逻辑变更先补充或更新测试。
4. 在现有边界内完成最小且完整的改动，避免顺手重构无关代码。
5. 更新受影响的专题文档、设计或调查记录。
6. 执行对应单元测试、完整单元测试和必要的构建/Lint。
7. 检查 diff、敏感信息、Room schema、Koin 注册和无关改动。
8. 用中文总结改动、验证结果和未完成的真机检查。

## 构建命令

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleRelease
```

Release 构建需要本地签名配置。`versionCode` 来自 Git 提交数量，`versionName` 来自 `gradle/libs.versions.toml`，Debug/Release 会追加构建类型、提交数和 Git SHA 后缀。

## Git 与交付

- 保留用户未提交的无关改动，不执行破坏性 Git 操作。
- 提交信息使用英文小写 Conventional Commits 类型和中文描述，例如 `fix: 修复受限页面识别错误`。
- 未经用户明确要求，不主动提交、推送或创建 Pull Request。
- 不声称未执行的测试、构建或真机验证已经通过。
