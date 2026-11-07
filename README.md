<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="128" height="128" alt="NodeFlow 应用图标">
</p>

<h1 align="center">NodeFlow</h1>

<p align="center">使用 Kotlin、Jetpack Compose 和 Material 3 构建的现代 V2EX Android 第三方客户端。</p>

NodeFlow 专注原生 Android 与 Material You 体验，提供主题、节点、回复、用户资料、通知、账户和设置等功能。NodeFlow 与 V2EX 官方没有从属关系。

## 下载

安装包见 [GitHub Releases](https://github.com/Mystery00/NodeFlow/releases)。标记为 Pre-release 的版本为测试包；正式版以发布页实际条目为准。当前最低支持 Android 10（API 29）。

## 编译

环境要求：JDK 21、Android SDK，以及能够访问 Google Maven 和 Maven Central 的网络环境。

```powershell
git clone https://github.com/Mystery00/NodeFlow.git
cd NodeFlow
.\gradlew.bat :app:assembleDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/`。Release 构建需要配置签名，详细步骤见[开发工作流](docs/development/workflow.md)。

## 贡献代码

欢迎通过 Issue 或 Pull Request 参与。开发与提交约定见[开发工作流](docs/development/workflow.md)，验证范围见[测试与验证](docs/development/testing.md)。Pull Request 请说明改动目的、验证结果和可能影响。

提交 Bug 时，请提供复现步骤、预期行为、实际行为、Android 版本和必要的脱敏日志；不要上传 Cookie、Token 或其他隐私数据。

## 文档

项目架构、开发规范、子系统说明、设计计划和调查记录统一收录在 [docs/](docs/index.md)。

## 开源协议

NodeFlow 使用 [Apache License 2.0](LICENSE) 开源。
