<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" width="128" height="128" alt="NodeFlow 应用图标">
</p>

<h1 align="center">NodeFlow</h1>

<p align="center">使用 Kotlin、Jetpack Compose 和 Material 3 构建的现代 V2EX Android 第三方客户端。</p>

NodeFlow 专注原生 Android 与 Material You 体验，提供主题、节点、回复、用户资料、通知、账户和设置等功能。本项目只支持 Android，不使用跨端框架。NodeFlow 与 V2EX 官方没有从属关系。

## 下载

正式安装包将在项目发布页提供：**下载地址待发布**。

## 编译

环境要求：JDK 21、Android SDK，以及能够访问 Google Maven 和 Maven Central 的网络环境。

```powershell
git clone <repository-url>
cd NodeFlow
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Debug APK 生成在 `app/build/outputs/apk/debug/`。Release 构建需要在本地配置签名信息，签名文件和密码不得提交到仓库。

## 贡献代码

1. Fork 仓库并从最新代码创建功能分支。
2. 开发前阅读 [AGENTS.md](AGENTS.md) 和 [项目文档索引](docs/index.md)。
3. 修改业务逻辑时补充或更新单元测试。
4. 提交前运行 `:app:testDebugUnitTest` 和 `:app:assembleDebug`，并按改动范围运行 Lint 或真机验证。
5. 提交信息使用 Conventional Commits 类型前缀和中文描述。
6. 创建 Pull Request，说明改动目的、实现方式、验证结果和可能影响。

提交 Bug 时，请提供复现步骤、预期行为、实际行为、Android 版本和必要的脱敏日志；不要上传 Cookie、Token 或其他隐私数据。

## 文档

项目架构、开发规范、子系统说明、设计计划和调查记录统一收录在 [docs/](docs/index.md)。

## 开源协议

NodeFlow 使用 [Apache License 2.0](LICENSE) 开源。
