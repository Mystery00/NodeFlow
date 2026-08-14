# UI、导航与状态管理

## UDF/MVI

Feature 层通常由 `XxxScreen.kt`、`XxxViewModel.kt`、`XxxUiState.kt` 和 `XxxUiEvent.kt` 组成。

- Screen 渲染不可变 UiState，并将用户意图转换为 UiEvent。
- ViewModel 使用 StateFlow 暴露状态并调用 UseCase。
- 加载、刷新、分页、空数据、成功、失败和受限访问应可明确区分。
- 一次性导航或提示沿用现有事件模式，不作为永久状态反复消费。
- 可测试的状态与布局计算不应隐藏在大型 Composable 中。

## Compose 与 Material 3

- 新增 UI 只使用 Compose，不新增 XML layout。
- 使用 `MaterialTheme`、Dynamic Color、深色模式和 edge-to-edge。
- 正确处理 WindowInsets 与 Scaffold padding，避免重复消费。
- 长列表使用惰性组件和稳定 key；状态收集应感知生命周期。
- 文案优先放入 string resource，并考虑字体缩放、无障碍语义和点击区域。

## 导航

根 NavHost 管理全屏详情页面，MainShell 管理带底部导航的一级页面。路由集中定义在 `navigation` 包，不在 Screen 中散落字符串。

- 外部深链和应用内链接共用 V2EX 链接解析规则。
- 参数必须可恢复，并按需编码/解码。
- 导航失败安全回退到浏览器或错误状态。
- 修改层级、转场或 padding 时运行导航和布局相关测试。
- 帖子列表和帖子详情中的节点 Chip 复用统一的节点标题/名称映射，并通过根导航进入节点详情页。
- 帖子列表对显式标记或依据服务端移动造成的时间顺序异常推断出的置顶主题展示“置顶”状态 Chip；它与节点 Chip 视觉一致，但不可点击且不提供按钮语义。
- 帖子详情中发帖人和每条回复作者的头像、用户名都通过根导航进入对应用户详情页；空用户名不产生导航。
- 节点详情页提供账号级节点屏蔽入口：未登录时进入现有登录页，已登录时必须二次确认；请求期间禁止重复提交，成功后由结果对话框返回上一页。
- 已位于首页时重复点击首页底部导航项，应展开顶部 App Bar、滚动主题列表到顶部并刷新；从其他一级页面点击首页只执行导航。
- 主页面底部导航固定为“首页、节点、消息、我的”。消息入口通过 Badge 展示未读数：超过 99 条显示 `99+`，加载完成后无法获取未读数时显示 `!`，加载中、未读数为 0 或未登录时不显示。消息页由 `MainShell` 内部 NavHost 管理；未登录时展示登录引导且不创建通知分页请求，通知中的用户和主题详情仍由根 NavHost 打开。
- 通知主题链接可携带目标回复楼层；主题详情按需顺序补页到目标楼层后滚动到对应回复并短暂高亮，楼层不存在时安全停留在主题顶部。
- 主题详情回复按页加载：进入只抓第 1 页，滚动接近尾部自动加载下一页；刷新重拉已加载页并保持位置；发表回复后补页到最后一页并定位新楼层。分页状态由 `TopicDetailPager` 承载，见 [`../plans/2026-07-18-topic-detail-reply-paging-design.md`](../plans/2026-07-18-topic-detail-reply-paging-design.md)。

相关设计：

- [`../plans/2026-06-29-navigation-layering-design.md`](../plans/2026-06-29-navigation-layering-design.md)
- [`../plans/2026-06-29-navigation-transition-design.md`](../plans/2026-06-29-navigation-transition-design.md)
- [`../plans/2026-07-12-v2ex-link-routing-design.md`](../plans/2026-07-12-v2ex-link-routing-design.md)
- [`../plans/2026-07-25-notification-bottom-navigation-design.md`](../plans/2026-07-25-notification-bottom-navigation-design.md)

## 排障入口

UI 状态异常时检查 UiEvent 到 ViewModel 的处理、UseCase 返回、StateFlow 更新和生命周期收集；导航异常时检查目的地、根/内部 NavController、参数编码、返回栈与 `V2exLinkParser`。
