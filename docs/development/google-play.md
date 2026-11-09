# Google Play 发布准备

## 商品详情草稿

首发语言暂按简体中文准备，应用名称为 **NodeFlow**，类型为免费应用，包名为 `app.mystery0.nodeflow`。

**简短说明**

使用 Material You 设计的 V2EX 第三方客户端，浏览主题与节点，阅读回复并参与讨论。

**完整说明**

NodeFlow 是一款开源的 V2EX 第三方 Android 客户端。你可以按节点浏览社区话题，阅读主题正文与回复，打开帖子中的图片和链接。

- 在首页查看主题，按分类探索节点，并将常用节点固定到首页。
- 查看富文本正文、回复楼层和用户资料。
- 登录已有的 V2EX 账号后可收藏主题、参与回复、查看通知和签到。
- 支持深色模式与 Material You 动态颜色，Android 12 及以上可跟随系统配色。

NodeFlow 由 Mystery0 独立开发，与 V2EX 官方无从属关系。部分内容和互动功能取决于 V2EX 的访问与登录要求。

## 上传素材

| 项目 | 文件 | 规格 |
| --- | --- | --- |
| 应用图标 | [ic_launcher-playstore.png](../../app/src/main/ic_launcher-playstore.png) | 512 × 512，RGBA PNG |
| 置顶大图 | [feature-graphic.png](../assets/google-play/feature-graphic.png) | 1024 × 500，RGB PNG |
| 手机截图 | [首页](../assets/google-play/phone/01-home.png)、[主题与回复](../assets/google-play/phone/02-topic.png)、[节点分类](../assets/google-play/phone/03-nodes.png)、[设置](../assets/google-play/phone/04-settings.png) | 1080 × 1920，RGB PNG |

截图来自 Android 模拟器中运行的 NodeFlow。主题、作者和回复使用虚构示例，通过本地网络替身输入应用，适合公开展示。以上截图名称也可用作上传时的替代文本。[Google Play 预览资源规范](https://support.google.com/googleplay/android-developer/answer/9866151?hl=zh-Hans)

## 构建与签名

在完成[签名配置](workflow.md)后，从待发布的提交运行 `./gradlew.bat :app:bundleRelease`。生成的 AAB 位于 `app/build/outputs/bundle/release/app-release.aab`。`versionCode` 取自当前提交历史长度，每次上传前对照 Play Console 的已用版本号。

应用的 `minSdk` 为 29，`targetSdk` 为 37。Google Play 自 2026 年 8 月 31 日起要求新手机应用至少以 API 36 为目标。首次上传时确认 Play App Signing 和上传密钥，并记录 Play 应用签名证书与既有 APK 签名证书的关系。若要保留现有用户的跨渠道覆盖安装能力，两条渠道分发的应用签名证书须一致。[目标 API 要求](https://support.google.com/googleplay/android-developer/answer/11926878?hl=zh-Hans) · [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756?hl=zh-Hans)

## 提交审核前

1. 创建应用并由开发者核对创建表单中的声明。随后保存商品详情，确认公开支持邮箱、分发地区、应用访问权限、目标受众及内容分级。浏览公开内容可使用访客模式。对需登录的回复、收藏与通知功能，准备审核专用账号及操作说明，覆盖验证码等可能影响审核的步骤。[应用内容设置](https://support.google.com/googleplay/android-developer/answer/9859455?hl=zh-Hans)
2. 发布含隐私联系方式的公开隐私政策，并在应用内提供入口。填写数据安全表单时，以拟上传版本核对数据流。登录凭据用于与 V2EX 交互，Cookie 和个人访问令牌在本地加密保存。用户提交的回复会发送至 V2EX，选取上传的图片会发送至所选图床。正文中的外链媒体可能向对应主机发起加载请求。后台提醒默认关闭，开启后会定期请求 V2EX。普通设置与会话排除在系统备份之外，Room 数据库可能随 Android 备份或迁移。当前登录流程使用已有 V2EX 账号。详见[网络与认证](../subsystems/network-auth.md)和[存储](../subsystems/storage.md)。[用户数据政策](https://support.google.com/googleplay/android-developer/answer/10144311?hl=zh-Hans) · [数据安全表单](https://support.google.com/googleplay/android-developer/answer/10787469?hl=zh-Hans)
3. 补齐公开社区内容的审核链路。当前代码有节点屏蔽，尚未找到应用内举报内容、举报用户、屏蔽用户，以及发布回复或上传图片前接受内容规则的流程。确定举报受理渠道和处理责任后，再将这些功能纳入送审版本。[用户生成内容政策](https://support.google.com/googleplay/android-developer/answer/9876937?hl=zh-Hans)
4. 先在测试轨道验证签名构建的访客浏览及登录后流程，再根据 Play Console 的正式版资格推进。若此个人开发者账号适用新账号测试要求，则须有至少 12 位测试人员连续参与封闭式测试 14 天，再申请正式版发布权限。[个人账号测试要求](https://support.google.com/googleplay/android-developer/answer/14151465?hl=zh-Hans)

## 隐私政策草稿

发布前补齐生效日期和可接收隐私请求的公开联系方式，并将政策正文发布到无需登录即可访问的网页。

**NodeFlow 隐私政策**

NodeFlow 由 Mystery0 开发，可用于访问 V2EX 社区。你可以用访客身份浏览公开内容。登录时，你输入的 V2EX 登录信息会通过 HTTPS 提交给 V2EX 验证。应用将用户名及维持登录所需的 Cookie 和个人访问令牌加密保存在设备上，用于执行收藏、回复、签到和查看通知等操作。退出登录会清理本地会话。启用新消息提醒后，应用会在后台定期使用该会话检查 V2EX 未读通知。你可在设置中关闭提醒。

你提交的回复会发送至 V2EX，并按社区规则展示和保留。你选取并上传的图片会发送到所选图床（V2EX 或 Imgur）。查看主题时，应用可能从 V2EX、图片或媒体所在的其他主机加载内容。相应服务可收到建立连接所需的网络信息，例如 IP 地址。V2EX 账号及服务上保存的内容由 V2EX 和对应的图床分别管理。

应用在设备上保存设置、阅读缓存、未提交的回复草稿和草稿图片记录。你可以从应用设置清理主题、节点和用户缓存，在回复编辑器处理草稿，或从 Android 设置清除应用存储以移除本机数据。清除本机数据不会删除已经发送到 V2EX 或图床的内容。登录会话和普通设置不纳入 Android 云备份或设备迁移，数据库内容可能随 Android 备份或迁移，具体取决于系统设置。备份的管理由 Android 提供。

隐私联系渠道：[发布前填入开发者隐私联系邮箱或咨询页面]。
