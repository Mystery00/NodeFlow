# V2EX 主题与回复感谢接口调查

## 调查范围

2026-08-30 使用已登录的浏览器会话调查 V2EX 主题与回复的“感谢”行为。真实写操作严格限定为主题 `1238073`：感谢主题一次、感谢第 20 楼回复一次。未对其他主题、回复或写接口执行操作。

本文只记录脱敏后的协议结构，不保存 Cookie、完整 `once`、账号凭据或响应正文中的敏感信息。

## 主题感谢

主题操作区通过 JavaScript 调用：

```javascript
thankTopic(topicId, once)
```

请求结构：

```text
POST /thank/topic/{topicId}?once={once}
请求体：空
响应类型：application/json
```

页面处理的 JSON 字段：

- `success`：业务操作是否成功。
- `message`：业务失败时展示的消息。
- `once`：操作后轮换的页面级令牌；成功和业务失败都会读取。

成功后主题操作区替换为“感谢已发送”，随后调用 `/ajax/money` 刷新网页余额。

## 回复感谢

第 20 楼页面元素对应回复 ID `18030612`，JavaScript 调用：

```javascript
thankReply(replyId)
```

请求结构：

```text
POST /thank/reply/{replyId}?once={页面级 once}
Referer: https://www.v2ex.com/t/{topicId}
X-Requested-With: XMLHttpRequest
请求体：空
```

实测响应：

- HTTP 状态码：`200`
- Content-Type：`application/json;charset=utf-8`
- 无重定向

响应字段契约与主题感谢相同：`success`、`message`、`once`。成功后 `#thank_area_{replyId}` 变为“感谢已发送”，并刷新网页余额。

## 令牌与状态结论

- 主题感谢与回复感谢共用主题页面中的同一个页面级 `once`。
- `once` 可能出现在主题感谢控件的 `onclick` 中，也由页面脚本变量持有；解析不能只依赖收藏链接。
- 每次感谢响应都可能轮换 `once`，包括 `success=false` 的业务失败响应。
- App 只在内存中的当前主题详情状态保存该令牌，不写入日志、数据库、缓存或文档。
- `/ajax/money` 只是网页余额刷新，不是感谢操作成功判定条件，App 无需调用。

## 实现约束

- 使用现有 Cookie、User-Agent、访问保护和异常映射。
- 请求必须携带主题页 Referer，并模拟网页 XHR 请求头。
- HTTP 成功不等于业务成功，必须解析 JSON 的 `success`。
- 业务失败也要保留响应返回的新 `once`，避免后续操作继续使用旧令牌。
- 自动化测试只使用测试替身或 MockWebServer，不访问真实 V2EX。
