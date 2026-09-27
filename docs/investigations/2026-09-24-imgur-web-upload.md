# Imgur 匿名网页上传协议调查

日期：2026-09-24
结论：`native-http-supported`（有限网页公开流程）
范围：使用 1×1 无隐私 PNG，通过匿名 `https://imgur.com/upload` 网页流程观察请求；未使用账号 Cookie、未注册 API 应用、未保存图片或完整响应。

## 观察到的网页流程

1. GET `https://imgur.com/upload`，页面提供文件选择入口；页面本身没有可直接提交的 HTML form，上传由 JavaScript 驱动。
2. 选择无隐私样例后，网页创建匿名隐藏相册并执行上传流程。
3. 浏览器网络记录显示网页使用以下 API 路径：
   - POST `/3/album?client_id=<REDACTED_CLIENT_ID>`
   - POST `/3/upload/checkcaptcha?client_id=<REDACTED_CLIENT_ID>`
   - POST `/3/upload?client_id=<REDACTED_CLIENT_ID>`
   - PUT `/3/album/<REDACTED_ALBUM_ID>?client_id=<REDACTED_CLIENT_ID>`
4. 创建相册响应同时提供脱敏的 album id 与 album deletehash；匿名流程将 deletehash 用作上传 multipart 的 album 值，并用于相册完成 PUT 凭据。成功页面使用相册分享页形态 `https://imgur.com/a/<REDACTED_ALBUM_ID>`，并加载图片直链形态 `https://i.imgur.com/<REDACTED_IMAGE_ID>.png`。真实 ID、deletehash 未写入仓库。

## 脱敏证据与限制

- 仅记录方法、主机、路径和人工占位符；没有写入 Cookie、Authorization、真实 client ID、相册/图片 ID、原始 HTML、完整响应正文或样例图片。
- HAR 仅保存在仓库外临时目录，不作为项目产物；未复制到 Git。
- 未验证 JPEG、GIF、WebP 的独立结果媒体类型，也未验证 GIFV/视频转换、429、登录墙、验证页、断网后的服务端结果。
- 页面成功流程依赖 JavaScript，并调用带公开 `client_id` 的 Imgur `/3` API 请求；适配器只从上传页或同源 JS 动态读取该值，不记录真实值，不共享 V2EX Cookie。

## 失败分类与原生 HTTP 判断

| 场景 | 本轮状态 | 结论 |
| --- | --- | --- |
| 初始上传页可访问 | 已观察 | 可提取网页公开 client_id |
| 无隐私 PNG 浏览器上传 | 已观察成功 | 已用脱敏 MockWebServer 覆盖 album/multipart 流程 |
| 原生 Android OkHttp 复现 | 已执行测试 | 仅动态读取公开 client_id；不访问真实 Imgur |
| 登录墙/验证页/429/普通错误 | 未验证 | 后续适配器只能归为交互或不可用状态 |
| 发送后断网/结果不确定 | 未验证 | 不应自动重放上传 |

因此结论更新为 **`native-http-supported`**：适配器使用独立 OkHttp client、CookieJar 和总 deadline，执行匿名 album/multipart upload。验证码、协议变化、非 2xx 或无安全直链均转为需交互/协议错误；不注册 API、不共享 V2EX Cookie，测试不访问真实 Imgur。

## 隐私检查

执行：

```powershell
rg -n "Cookie|Authorization|<SESSION|<TOKEN|完整响应|原始 HTML" docs/investigations/2026-09-24-imgur-web-upload.md
```

预期命中仅为本报告的脱敏说明、禁止记录说明或占位符；真实凭据、真实 ID、图片数据和完整响应未写入。
