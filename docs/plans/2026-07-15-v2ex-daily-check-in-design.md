# V2EX 每日签到设计

## 目标

在“我的”页面提供每日签到入口，通过 NodeFlow 现有 Retrofit/OkHttp 登录会话领取奖励；成功后刷新账号概览，并使用 Toast 显示签到结果。

## 技术选择

采用纯 Retrofit/OkHttp 方案，不使用隐藏 WebView。签到与现有业务请求共享 `V2exCookieJar`、`AuthInterceptor`、`UserAgentInterceptor` 和重定向能力，不创建第二套 Cookie 或网络链路。

当前真实调查只证明 Chrome 导航能够完成领取，尚未证明 OkHttp 一定能通过 V2EX 的浏览器指纹检查。因此首版把风控页作为明确失败处理，不进行 WebView 自动降级。

## 请求流程

1. 每次点击签到都重新请求 `GET /mission/daily`。
2. 排除登录页、`/restricted`、Cloudflare 和浏览器风控页面。
3. 解析签到状态；已经签到时不调用领取接口。
4. 从领取控件中读取当次纯数字 `once`；缺失或非法时停止。
5. 调用 `GET /mission/daily/redeem?once=...`，Referer 固定为 `https://www.v2ex.com/mission/daily`，并允许 OkHttp 跟随重定向。
6. 要求最终 URL 回到 `/mission/daily`，领取控件消失，余额控件出现，且页面包含正向成功结构。
7. 成功后请求 `/balance`，从页面中第一条“每日登录”流水的下一列读取正整数铜币奖励。
8. 刷新账号概览；奖励可用时显示“签到成功，获得 N 铜币”，否则显示“签到成功”。

## 状态与错误处理

- ViewModel 使用活动 Job 阻止重复点击，请求期间禁用签到按钮。
- `once` 只存在于 RemoteDataSource 单次调用栈中，不持久化、不进入 UI 状态，并在网络日志中按查询参数脱敏。
- 登录失效映射为 Auth 错误并清理会话；受限和风控页面映射为 AccessDenied；页面结构或最终 URL 异常映射为 Parse。
- Toast 文案存放在 UiState 中，界面展示后立即发送消费事件，避免重组时重复显示。

## 验证边界

自动化测试使用测试替身和 MockWebServer，不访问真实 V2EX。今天只允许在模拟器检查安装、启动、页面布局和已签到状态，不点击真实领取按钮；首次真实 OkHttp 签到留待账号次日处于未签到状态时由用户验证。
