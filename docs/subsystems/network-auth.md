# 网络、访问控制与登录

## 数据来源

NodeFlow 同时使用 V2EX 公开 JSON API、HTML 页面和需要登录态的网页接口。JSON API 不足以支持的节点列表、登录、签到、通知、发帖和回复等能力，应通过现有 HTML/表单链路实现。

- Retrofit 基础地址为 `https://www.v2ex.com/`。
- 请求声明位于网络 API，调用和响应处理位于 RemoteDataSource。
- Feature、ViewModel 和 Compose UI 不直接拼接或执行请求。
- User-Agent、Referer、Origin 和 Cookie 会影响响应，不得随意修改。

## 页面分类与错误处理

V2EX 可能返回正常内容、登录页、`/restricted`、首页重定向、Cloudflare 页面或非预期 HTML。RemoteDataSource 应结合最终响应 URL和 DOM 结构先分类，再交给业务 Parser；不能仅根据 HTTP 200 或可见文案判断成功。

- 登录表单等结构化特征优先于易变化的文案。
- 受限访问与普通网络错误应映射为不同业务错误。
- 不把 Retrofit、OkHttp 和 Jsoup 底层异常直接暴露给 UI。
- 不因非关键缓存写入失败丢弃已经成功获取的远端数据。

相关历史设计：

- [`../plans/2026-07-13-v2ex-restricted-content-access-design.md`](../plans/2026-07-13-v2ex-restricted-content-access-design.md)
- [`../plans/2026-07-13-v2ex-restricted-content-access.md`](../plans/2026-07-13-v2ex-restricted-content-access.md)
- [`../investigations/2026-07-13-v2ex-daily-check-in.md`](../investigations/2026-07-13-v2ex-daily-check-in.md)

## 登录态与隐私

- 复用 `V2exCookieJar`、CookieStorage、AuthInterceptor、SessionStore 和相关 Repository，不建立第二套会话状态。
- 完整保留 Cookie 的 domain、path、expiry、secure 等语义。
- 清除登录态时同步清理 Cookie、Token 和内存状态。
- 登录数据继续排除在 Android 系统备份和设备迁移之外。
- 日志只记录必要的英文诊断上下文，不记录凭据、完整响应正文和用户内容。

## 通知页面

- 通知中心通过 `GET /notifications?p={page}` 获取登录用户的历史通知，分页大小按页面实际结构处理。
- RemoteDataSource 必须校验最终 URL、登录页和受限页，再将 HTML 交给 Parser；不得把异常页面解析成空通知。
- 通知正文来自页面 `.payload`，主题、触发用户、相对时间和回复楼层从结构化链接与元素提取。
- 只有正文明确包含可验证的 `@用户 #楼层` 时，Repository 才通过主题详情分页器补页到目标楼层并补全引用摘要；同批次内同主题一次失败后不再重试，补全失败时保留通知正文并省略引用卡片。
- 通知列表只读，不实现页面提供的删除操作，也不记录通知正文或账号信息。

## 节点屏蔽

- 节点屏蔽使用 V2EX 网页接口 `GET /settings/ignore/node/{id}?once={once}`，节点 ID 来自现有节点信息，`once` 只从同一节点的屏蔽/收藏 action 链接提取。
- 屏蔽请求复用现有 Cookie、桌面节点页请求和访问保护；登录页、受限页、首页重定向或未回到节点路径的响应不得视为成功。
- `once` 只在单次请求内存中使用，不写入模型、缓存、日志或文档。

## 排障入口

排查登录或访问失败时依次检查：最终响应 URL、页面分类、Cookie 是否加载、User-Agent/Referer/Origin、错误映射、Repository 是否错误回退缓存，以及对应 MockWebServer/Parser 测试。
