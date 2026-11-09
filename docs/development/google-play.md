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
2. 将公开的 [NodeFlow 隐私政策](../../PRIVACY.md)链接填入 Play Console，并在应用内提供入口。填写数据安全表单时，以拟上传版本核对数据流。详见[网络与认证](../subsystems/network-auth.md)和[存储](../subsystems/storage.md)。[用户数据政策](https://support.google.com/googleplay/android-developer/answer/10144311?hl=zh-Hans) · [数据安全表单](https://support.google.com/googleplay/android-developer/answer/10787469?hl=zh-Hans)
3. 补齐公开社区内容的审核链路。当前代码有节点屏蔽，尚未找到应用内举报内容、举报用户、屏蔽用户，以及发布回复或上传图片前接受内容规则的流程。确定举报受理渠道和处理责任后，再将这些功能纳入送审版本。[用户生成内容政策](https://support.google.com/googleplay/android-developer/answer/9876937?hl=zh-Hans)
4. 先在测试轨道验证签名构建的访客浏览及登录后流程，再根据 Play Console 的正式版资格推进。若此个人开发者账号适用新账号测试要求，则须有至少 12 位测试人员连续参与封闭式测试 14 天，再申请正式版发布权限。[个人账号测试要求](https://support.google.com/googleplay/android-developer/answer/14151465?hl=zh-Hans)
