# 网络、访问控制与登录

## 数据来源

NodeFlow 同时使用 V2EX 公开 JSON API、HTML 页面和需要登录态的网页接口。节点列表、登录、签到、通知和回复等能力复用 HTML/表单链路；当前没有发布新主题的界面与用例，不将“创建回复”描述为“支持发帖”。

- Retrofit 基础地址为 `https://www.v2ex.com/`。
- 请求声明位于网络 API，调用和响应处理位于 RemoteDataSource。
- Feature、ViewModel 和 Compose UI 不直接拼接或执行请求。
- User-Agent、Referer、Origin 和 Cookie 会影响响应，不得随意修改。
- 回复图片上传复用既有 V2EX 认证客户端，通过 `V2exImageTransport` 交给独立 `V2exImageHostAdapter`；上传页、登录页、挑战页和非预期响应先分类，发送后结果不确定时不自动重传。
- `ImgurHttpImageHostAdapter` 已实现匿名网页 HTTP 流程：从上传页或允许主机列表内的脚本动态读取公开 client_id，依次创建 album、检查 captcha、上传 multipart 和完成 album；不硬编码 client_id，不共享 V2EX Cookie。默认独立客户端禁用重定向和连接自动重试，每个 HTTP call 超时 30 秒；没有整个上传流程的总 deadline 或串行 Mutex。
- Imgur 挑战页识别返回 `InteractionRequired` 和 `OpenHostPage("https://imgur.com/upload")`。协议变化、无安全直链和一般非 2xx 响应按具体情况分类，未必带网页恢复动作；不能笼统描述为所有失败都可打开网页。手动网页上传不会自动回填 App。
- V2EX descriptor 上限为 6 MiB，Imgur 为 10 MiB，均声明 PNG/JPEG/GIF/WebP。app 的 `AndroidImageContentReader` 只读取至 6 MiB + 1 字节用于后续判断，没有为 Imgur 扩展到 10 MiB；因此不能只凭 descriptor 宣称 app 已完整支持 10 MiB 上传。
- 历史调查中的 `native-http-supported` 表示代码和本地替身覆盖了 HTTP 流程，不是当前真实服务可用性保证。浏览器观察、MockWebServer 测试和 Android 原生客户端真实上传应分别记录，详见[调查记录](../investigations/2026-09-24-imgur-web-upload.md)。

## 页面分类与错误处理约束

V2EX 可能返回正常内容、登录页、`/restricted`、首页重定向、Cloudflare 页面或非预期 HTML。RemoteDataSource 应结合最终响应 URL和 DOM 结构先分类，再交给业务 Parser；不能仅根据 HTTP 200 或可见文案判断成功。

- 登录表单等结构化特征优先于易变化的文案。
- 受限访问与普通网络错误应映射为不同业务错误。
- 新增链路应将底层异常映射为用户可理解的错误。当前 `safeNetworkCall` 对 IO、序列化等异常分类，但兜底分支以及 `Throwable.toUserMessage` 仍可能保留异常 message，不能声称所有异常内容都经过统一脱敏。
- 非关键缓存写入应避免覆盖远端成功结果；当前部分 Repository 仍在成功链路直接写缓存，实际边界见[存储说明](storage.md)。

以上是新增和维护协议时的约束，不代表每条既有链路都已实现同等校验。回复提交的当前例外见下一节。

## 创建回复的当前行为

`ReplyRemoteDataSource` 提交前读取主题表单和末页，校验表单 action 的来源、端口与主题路径，用提交前末楼层加一推算新楼层。请求复用关闭连接重试的 `v2exWriteClient`，表单 body 标记为一次性，不自动重发。

当前 POST 返回非 2xx 时报告失败；返回 2xx 时关闭响应体并直接返回 `CreateReplyResult.Success`，没有解析最终响应中的错误、登录/挑战页面，也没有提交后回读确认。编辑器据此关闭、清空正文并尝试删除本地草稿。现有 `ReplyRemoteDataSourceTest` 明确覆盖“无法解析的 200 响应仍视为成功”；历史计划中的“按账号、正文、新回复 ID 确认成功”和 `SubmitUnconfirmed` 流程不是当前实现。

维护回复链路时，应同时核对该成功判定、[草稿按主题保存](storage.md)及对应回归测试，不将历史计划目标当作当前保证。

相关历史设计：

- [`../plans/2026-07-13-v2ex-restricted-content-access-design.md`](../plans/2026-07-13-v2ex-restricted-content-access-design.md)
- [`../plans/2026-07-13-v2ex-restricted-content-access.md`](../plans/2026-07-13-v2ex-restricted-content-access.md)
- [`../investigations/2026-07-13-v2ex-daily-check-in.md`](../investigations/2026-07-13-v2ex-daily-check-in.md)

## 登录态与隐私

- 复用 `V2exCookieJar`、CookieStorage、AuthInterceptor、SessionStore 和相关 Repository，不建立第二套会话状态。
- Cookie、Personal Access Token 和会话数据只以密文落盘；生产实现使用 Android Keystore 中不可导出的 AES-256 密钥与 AES/GCM，应用私有存储只保存版本化 IV 与密文载荷。
- CookieStorage 保留 host-only/origin、domain、path、expiry、persistent、secure 和 HttpOnly 属性；但 Session 还保存 Cookie header 快照。`AuthInterceptor` 在目标 URL 没有可用 Cookie 时可由快照恢复，该路径只恢复名称和值，并设置目标 domain 与 `/` 路径，无法保留原 expiry、secure 等属性。
- `SessionStore.save/clear` 由自身 Mutex 串行化，CookieJar 方法使用同步锁；登出依次清理 Cookie、Session 和标签。没有跨组件原子事务或统一会话世代，旧请求仍可能回写 Cookie/标签；当前不提供并发情况下的完整登出隔离保证。
- 密文损坏、认证标签失败或密钥不可用时尝试删除对应存储并返回空值，不记录原始数据；存储删除失败仍可能抛错。
- 安全存储、旧 Cookie 文件和整个普通设置 DataStore 文件均排除在系统云备份和设备迁移之外，Room 数据库没有被排除。
- 日志只记录必要的英文诊断上下文，不记录凭据、密文、完整响应正文和用户内容。

## 通知页面与后台提醒

- 通知中心通过 `GET /notifications?p={page}` 获取登录用户的历史通知，分页大小按页面实际结构处理。
- RemoteDataSource 必须校验最终 URL、登录页和受限页，再将 HTML 交给 Parser；不得把异常页面解析成空通知。
- 通知正文来自页面 `.payload`，主题、触发用户、相对时间和回复楼层从结构化链接与元素提取。
- 只有正文明确包含可验证的 `@用户 #楼层` 时，Repository 才通过主题详情分页器补页到目标楼层并补全引用摘要；同批次内同主题一次失败后不再重试，补全失败时保留通知正文并省略引用卡片。
- 通知列表不实现页面提供的删除操作，也不记录通知正文或账号信息；访问通知页面本身会让服务端标记已读，因此真实账号的严格只读验证不应打开通知页。
- 前台未读角标由内存共享状态维护：账户首页和正常帖子分页的既有响应可更新数量，不为角标新增请求。帖子必须来自可信 HTTPS 来源及对应主题路径，且成功解析为正常主题；只接受通知入口中的显式数量，缺失、异常和纯缓存响应保留旧值，正文中的伪造通知链接不参与解析。
- 通知页通过地址、页面分类和通知内容校验后更新未读数：优先采用页面显式数量，否则确认已读为 0；加载失败或纯缓存响应不清零。请求绑定会话及递增序号，较早请求不能覆盖较新成功响应，退出或切换会话清空内存状态。后台提醒的检查频率和持久化基线不变。
- 后台提醒默认关闭；开启后使用 WorkManager 每 30 分钟的周期任务和联网约束检查可信的 V2EX HTTPS 首页，实际运行时间由系统调度。匿名首页或登录跳转视为认证失效并结束本次工作，受限页、访问挑战和临时网络/服务异常交由 WorkManager 重试。
- 上次未读数量存于加密安全存储：首次观察到正数或数量增加时提醒；数量不变或下降时不重复提醒；降为 0 时重置基线。关闭提醒时清除基线，运行中的检查在写入前后重新读取设置，发布通知前再次检查开关。这个基线没有账号命名空间，登出路径也不负责清除它；这些检查不是与账号切换共享的原子事务。

## 收藏主题列表

收藏列表通过现有认证客户端以桌面模板请求 `GET /my/topics?p={page}`，复用 Cookie 与主题条目解析。必须校验最终 HTTPS 来源、路径、页码，以及登录、受限表单和访问挑战；未识别的页面不能作为空收藏。列表只提取主内容区收藏容器中的主题，分页依据页面链接和 `page_input.max`，不按条目数量猜测末页，也不按回复时间异常推断置顶。收藏列表使用独立 PagingSource 去重，并从第一页刷新；不写入首页缓存或 Room。

当前桌面模板的收藏标题位于 `#Main .box > .cell > h2`，标题和主题列表分属不同的 `.box`。解析器兼容旧版 `.header`，在主内容区识别标题后读取该区的列表与分页，排除侧栏；这两种模板结构均有脱敏回归测试。

## 主题与回复感谢

主题和回复感谢均使用主题页中的页面级 `once`，通过现有认证客户端发送空请求体的 XHR POST：`/thank/topic/{topicId}?once={once}` 或 `/thank/reply/{replyId}?once={once}`。请求携带主题页 Referer，响应必须按 JSON 的 `success`、`message` 和轮换后的 `once` 处理；不能仅依据 HTTP 200 判断成功。`once` 只保留在当前主题详情的内存状态中，不写入日志、数据库或普通缓存。网页成功后的 `/ajax/money` 余额刷新不是感谢操作的一部分，客户端不依赖它。

## 节点屏蔽

- 节点屏蔽使用 V2EX 网页接口 `GET /settings/ignore/node/{id}?once={once}`，节点 ID 来自现有节点信息，`once` 只从同一节点的屏蔽/收藏 action 链接提取。
- 屏蔽请求复用现有 Cookie、桌面节点页请求和访问保护。2026-08-16 实测屏蔽成功后 V2EX 会重定向到首页，因此写操作结果允许停留在可信的 V2EX HTTPS 页面；登录页、受限页、受限登录表单、结构化访问挑战页或站外跳转仍不得视为成功。挑战页按 DOM、页面标题和完整风控提示识别，不能因首页主题正文提到 Cloudflare 而误判。
- `once` 只在单次请求内存中使用，不写入模型、缓存、日志或文档。

## 排障入口

排查登录或访问失败时依次检查：最终响应 URL、页面分类、Cookie 是否加载、User-Agent/Referer/Origin、错误映射、Repository 是否错误回退缓存，以及对应 MockWebServer/Parser 测试。
