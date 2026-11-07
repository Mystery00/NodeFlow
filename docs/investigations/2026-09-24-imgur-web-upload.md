# Imgur 匿名网页上传协议调查

日期：2026-09-24
结论：`native-http-supported`（已有原生 HTTP 适配器及本地协议替身测试；不表示已验证当前 Android 客户端对真实 Imgur 的上传可用性）
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
- 页面成功流程依赖 JavaScript，并调用带公开 `client_id` 的 Imgur `/3` API 请求；当前适配器从上传页或允许主机列表内的 JS 动态读取该值，不记录真实值，不共享 V2EX Cookie。

## 失败分类与原生 HTTP 判断

| 场景 | 本轮状态 | 结论 |
| --- | --- | --- |
| 初始上传页可访问 | 已观察 | 可提取网页公开 client_id |
| 无隐私 PNG 浏览器上传 | 历史观察成功 | 浏览器证据与原生客户端真实上传分开判断 |
| Kotlin/JVM 适配器协议测试 | 本地 MockWebServer 覆盖 | 动态提取 client_id 和 album/multipart 流程，不访问真实 Imgur |
| Android 原生客户端真实上传 | 本记录没有对应验证证据 | 不能用浏览器成功或本地替身测试代替 |
| 登录墙/验证页/429/普通错误 | 真实服务场景未验证 | 当前代码分类见下方说明 |
| 发送后断网/结果不确定 | 真实服务场景未验证 | 当前客户端关闭自动连接重试，不自动重放上传 |

## 当前实现核对

实际类为 `ImgurHttpImageHostAdapter`，由 app 注册到 `DefaultImageHostRegistry`。它使用独立 OkHttp client 和内存 CookieJar，默认禁用重定向及连接自动重试，**单次 HTTP call 超时为 30 秒**；历史计划中的整体 deadline、一次上传独立会话和串行 Mutex 没有实现，不能作为当前保证。

挑战页返回 `InteractionRequired` 并附带 `OpenHostPage`；协议解析失败或无安全直链返回 `ProtocolChanged`，429 返回 `RateLimited`，其他 4xx/5xx 按权限/服务错误分类，未必提供网页恢复动作。图片上传或完成阶段结果不确定时保留 `Unknown`，app 据此显示结果无法确认。外部浏览器的手动上传不会自动回填 App。

`native-http-supported` 在此仅表示已实现这套 HTTP 协议路径；实际调用、能力上限和当前限制以[网络说明](../subsystems/network-auth.md)及源码为准。

## 隐私检查

执行：

```powershell
rg -n "Cookie|Authorization|<SESSION|<TOKEN|完整响应|原始 HTML" docs/investigations/2026-09-24-imgur-web-upload.md
```

预期命中仅为本报告的脱敏说明、禁止记录说明或占位符；真实凭据、真实 ID、图片数据和完整响应未写入。
