# V2EX 每日签到调查记录（2026-07-13）

> 本文只保存已验证事实、公开实现证据和后续调查清单，不是最终设计方案。正式设计、实施计划和代码均留待后续确认。

## 调查目标与约束

- 为 NodeFlow 增加 V2EX 每日签到能力。
- 使用真实账号和带界面的 `agent-browser` 调查页面、请求与响应。
- 登录由用户在可见浏览器中填写凭据，工具只负责点击登录和读取脱敏后的结果。
- 一个账号每天只能领取一次奖励，因此不能重复执行真实签到。
- 后续实现成功后需要显示 Toast。
- 不使用模拟器执行真实签到验证；允许执行不访问真实签到接口的 JVM 单元测试。

## 会话与敏感信息处理

- 浏览器持久会话名：`nodeflow-v2ex`。
- 登录成功后会话由 `agent-browser --session-name` 自动保存和恢复。
- 本文不记录用户名、密码、Cookie、验证码或真实 `once`。
- 工作区原有未跟踪文件 `password.txt` 未被读取或修改。
- 所有接口示例都把一次性参数写成 `once=<redacted>`。

## 2026-07-13 实际观察

### 登录与每日任务页

1. 打开 `https://www.v2ex.com/mission/daily` 时，未登录会跳转至：

   ```text
   /signin?next=%2Fmission%2Fdaily
   ```

2. 用户填写登录表单后，由工具点击提交控件，最终进入：

   ```text
   GET https://www.v2ex.com/mission/daily
   HTTP 200
   ```

3. 网络记录中没有出现 `/mission/daily/redeem`，说明本次登录操作没有触发签到。

4. 当前账号在本轮开始前已经签到，页面具备以下特征：

   - 包含正向确认文案“每日登录奖励已领取”。
   - 包含连续登录天数信息。
   - 操作控件文案为“查看我的账户余额”。
   - 操作控件目标为 `/balance`，不再包含 `once`。

### 余额页奖励流水

对 `GET https://www.v2ex.com/balance` 做了脱敏的结构化读取：

- 存在包含“每日登录”的流水行。
- 该行共 5 列。
- 描述列索引为 1，奖励变化列索引为 2。
- 本次样本中的最新每日登录奖励为正整数，具体值不记录。
- 奖励数值只用于验证解析结构，不能写死到业务逻辑中。

## 尚未通过本账号实测的内容

由于账号已经签到，本次无法捕获以下成功链路：

- 未签到页面上的真实领取按钮及真实 `once`。
- `/mission/daily/redeem` 的实际请求状态码和重定向链。
- 领取响应的 `Content-Type` 和完整 HTML 结构。
- 成功领取后页面从“可领取”变为“已领取”的同一次会话变化。

这些项目应在下一次账号处于未签到状态时优先补齐，并在领取前先清空浏览器网络日志。

## 2026-07-15 实际领取补充调查

本次继续使用 `nodeflow-v2ex` 持久浏览器会话。原 Cookie 已过期，用户在可见浏览器中重新登录；工具没有读取或保存账号密码。确认所有签到前数据已经采集后，工具只执行了一次真实领取。

### 签到前页面

`GET /mission/daily` 的最终响应为 `text/html`、HTTP 200。页面处于可领取状态，具备以下结构：

- 领取控件为不属于表单的 `<input type="button">`。
- `onclick` 使用 `location.href` 发起浏览器导航。
- 目标路径为 `/mission/daily/redeem`。
- 查询参数只有 `once`，实测值为纯数字且已脱敏。
- 控件文案为“领取 X 铜币”，不能从签到前页面取得实际奖励数值。
- 页面没有“每日登录奖励已领取”标记。
- 页面显示连续登录天数，具体值不记录。

签到前读取 `/balance` 作为基线：可以定位到上一条“每日登录奖励”流水并解析出正整数奖励，具体日期和数值不记录。流水行共 5 列，描述列索引为 1，奖励变化列索引为 2。

### 唯一一次领取请求

领取前已清空浏览器网络日志，随后只点击一次领取控件。脱敏后的主文档请求链为：

```text
GET /mission/daily/redeem?once=<redacted>
    -> 1 次重定向
GET /mission/daily
    -> HTTP 200 text/html
```

浏览器最终地址为 `/mission/daily`，最终 URL 不再包含 `once`。本次真实浏览器导航没有进入登录页、受限页、Cloudflare 页面或“干净安装的浏览器”风控页。

### 签到后页面

重定向后的每日任务页同时满足以下成功条件：

- 出现“每日登录奖励已领取”。
- 出现“已领取”“成功领取”和“已成功”等正向文案。
- `/mission/daily/redeem` 领取控件已经消失。
- 出现“查看我的账户余额”控件，目标为 `/balance`。
- 连续登录天数增加 1。
- 页面不含登录表单。

因此，可靠实现应以最终页面分类、领取控件消失和正向成功结构共同确认结果，不能只依赖 HTTP 2xx。

### 签到后余额

刷新 `/balance` 后，出现新的“每日登录奖励”流水，并能解析出正整数奖励；具体数值不记录。页面结构仍为 5 列：描述列索引 1，奖励变化列索引 2。

实际奖励只能在签到后的余额流水中可靠取得。成功 Toast 可显示“签到成功，获得 N 铜币”这一动态格式，但代码必须解析实际响应，不能写死本次样本值。

### 对实现的直接约束

- `once` 必须在每次领取前重新读取，不能持久化、复用或写入日志。
- 领取必须表现为从 `/mission/daily` 发起的浏览器 GET 导航，并携带现有登录 Cookie。
- 客户端必须允许并检查重定向后的最终 URL；本次实测最终回到 `/mission/daily`。
- 最终页面必须先排除登录、受限、Cloudflare 和浏览器风控页面，再解析签到状态。
- 成功后重新读取 `/balance`，从最新“每日登录奖励”行的下一列解析铜币奖励。
- 原生 Retrofit/OkHttp 是否能通过领取端点的浏览器指纹检查仍未被本次测试证明；本次成功发生在真实 Chrome 导航中。

## 公开实现提供的补充证据

下面的内容来自公开客户端实现，只作为交叉验证，不替代下一次真实抓取。

### 领取入口

公开实现记录的未签到控件形态为：

```html
<input
    type="button"
    value="领取 N 铜币"
    onclick="location.href = '/mission/daily/redeem?once=<redacted>'"
/>
```

由此可确定：

- 请求方法是浏览器导航产生的 `GET`。
- 路径为 `/mission/daily/redeem`。
- 查询参数名为 `once`，值来自当天每日任务页，不能缓存或复用。
- 来源页应为 `https://www.v2ex.com/mission/daily`。

### 成功判定

可靠实现不能仅凭 HTTP 2xx、领取按钮消失或页面没有报错来判定成功。公开实现要求响应页面出现以下正向文案之一：

- “每日登录奖励已领取”
- “已领取”
- “成功领取”
- “已成功”

如果领取按钮仍存在，或者页面出现“请用一个干净安装的浏览器重试”等风控提示，应判定失败，不能更新本地已签到状态。

### 奖励数值

领取响应本身不稳定地携带奖励数值。公开实现会在确认领取成功后访问 `/balance`，找到最新“每日登录”流水，并读取描述列之后的奖励变化列。Toast 可以优先显示：

```text
签到成功，获得 N 铜币
```

奖励解析失败时退化为：

```text
签到成功
```

### 浏览器指纹风险

公开客户端曾尝试使用普通 HTTP 客户端调用领取接口，但实机上即使补齐浏览器 User-Agent、Referer 和客户端提示头，也可能被 V2EX 的 TLS/JavaScript 指纹检测拒绝。该客户端最终使用隐藏 WebView 完成以下真实浏览器导航：

```text
/mission/daily
    -> /mission/daily/redeem?once=<redacted>
    -> 正向确认领取成功
    -> /balance
```

因此，后续设计至少要比较“直接 Retrofit 请求”和“隐藏 WebView 导航”两类方案，不能只根据接口形式假设原生 HTTP 一定可靠。

### 公开证据链接

- [修正 onclick 领取路径并增加奖励 Toast](https://github.com/honjow/Next2V/commit/997f8448e156c9be98e3a6c1cb3c92f6cba9a0f4)
- [使用隐藏 WebView 执行签到](https://github.com/honjow/Next2V/commit/54044c75c9d692ce6cac2548b44411a168aab05b)
- [要求正向成功文案，避免风控页被误判为成功](https://github.com/honjow/Next2V/commit/c806e6cfdfb5f5341cd7c1cb4b015323011264ce)
- [普通 HTTP 请求补齐 User-Agent 与 Referer 的尝试](https://github.com/honjow/Next2V/commit/abe470d453bf7407bd4b31cc111a57d682916338)

## NodeFlow 现有能力

仓库已经完成读取和展示签到状态的半条链路。

### 网络层

`app/src/main/java/app/mystery0/nodeflow/core/network/V2exRawApi.kt`

- 已有 `dailyMission()`：`GET /mission/daily`。
- 已有 `balance()`：`GET /balance`。
- 尚无领取接口。

### 模型与解析

`app/src/main/java/app/mystery0/nodeflow/core/model/Models.kt`

- `DailyCheckIn.checkedIn`
- `DailyCheckIn.continuousDays`
- `DailyCheckIn.redeemOnce`

`app/src/main/java/app/mystery0/nodeflow/core/parser/V2exHtmlParser.kt`

- `parseDailyCheckIn()` 已能识别 `/balance` 为已签到。
- 已能从 `/mission/daily/redeem?once=...` 中提取 `once`。
- 已能解析连续签到天数。

### 数据与界面

- `AccountRemoteDataSource.overview()` 已请求每日任务页和余额页。
- `AccountOverviewRepository` 目前只有只读 `overview()`。
- `AccountViewModel` 尚无签到事件和防重复提交状态。
- `AccountScreen` 目前只显示“待签到/已签到”，没有签到按钮。
- 项目尚无 Android Toast 调用；设置页已有“一次性消息展示后消费”的状态模式可借鉴。
- 项目已有 `AndroidView + WebView + evaluateJavascript` 使用经验，见 `RichHtmlText.kt`，隐藏 WebView 方案不需要引入新的 WebView 依赖。

## 2026-07-15 补充调查执行情况

1. 已使用现有 `nodeflow-v2ex` 持久会话重新打开 `/mission/daily`。
2. 已确认页面处于未签到状态，并记录脱敏后的按钮标签、路径和参数名。
3. 已清空浏览器网络请求日志。
4. 已且仅点击一次真实领取按钮。
5. 已记录并脱敏：
   - 请求方法与路径；
   - 状态码；
   - 重定向链与最终 URL；
   - `Content-Type`；
   - 成功页正向文案；
   - 领取按钮是否消失；
   - `/balance` 最新“每日登录”奖励列。
6. 下一步基于真实结果比较 2～3 种实现方案并确定推荐方案。
7. 向用户提交架构、数据流、错误处理、Toast 和测试设计，获得批准后再写正式设计文档。
8. 正式设计批准前不修改生产代码。

## 当前结论

2026-07-15 已补齐一次真实的“未签到页面 → 唯一一次领取 → 成功页面 → 余额奖励”完整链路。已确认领取接口、参数来源、重定向、成功判定、连续天数变化和奖励解析结构。

后续设计已确定采用纯 Retrofit/OkHttp 方案，不引入隐藏 WebView；详见 [`../plans/2026-07-15-v2ex-daily-check-in-design.md`](../plans/2026-07-15-v2ex-daily-check-in-design.md)。本次实测只能证明真实 Chrome 导航成功，原生 HTTP 客户端能否通过浏览器指纹检查仍需在账号下一次未签到时由应用实际验证。
