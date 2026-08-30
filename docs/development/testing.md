# 测试与验证

## 基本要求

NodeFlow 明确维护 JVM 单元测试。修改业务逻辑必须补充或更新对应测试，修复 Bug 时优先先写能复现问题的失败测试。

应重点测试：

- UseCase、Repository、RemoteDataSource、LocalDataSource 和缓存策略。
- HTML Parser、页面分类、链接解析、格式化与图片分类。
- ViewModel 的 UiState/UiEvent 转换。
- Room DAO、Entity 映射，以及正式发布后的 Migration；当前未发布版本只维护完整版本 1 schema。
- Cookie、加密会话存储、User-Agent、访问控制、后台通知去重和网络异常映射。

网络测试使用 MockWebServer 或测试替身，不访问真实 V2EX。协程和 Flow 使用 `kotlinx-coroutines-test`，断言优先使用 Truth。HTML fixture 必须最小化且脱敏。

## 最低验证矩阵

| 改动 | 必须执行 |
| --- | --- |
| 业务逻辑 | 相关局部测试、`:app:testDebugUnitTest` |
| Kotlin 或构建配置 | `:app:testDebugUnitTest`、`:app:assembleDebug` |
| UI、资源、Manifest、导航 | 相关测试、`:app:assembleDebug`，按需 `:app:lintDebug` |
| Room Entity/DAO/schema | 数据库测试、全量测试、Debug 构建、schema 检查；正式发布后的版本升级还必须运行 Migration 测试 |
| 网络、Cookie、登录、HTML 解析 | 相关 MockWebServer/Parser 测试、全量测试 |
| Release、混淆、签名 | 在安全配置可用时运行 Release 构建 |

## 真机或模拟器验证

以下内容不能只依靠 JVM 测试：

- Dynamic Color、深色模式、edge-to-edge、系统栏和字体缩放。
- 导航返回栈、外部深链和浏览器回退。
- 登录页面、Cookie 持久化和真实受限页面。
- 图片加载、SVG、正文图片与大图缩放。
- 网络切换、离线缓存和进程重启后的恢复。

无法执行某项验证时，应在最终任务总结中明确说明原因和剩余风险。
