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
- 已位于首页时重复点击首页底部导航项，应滚动主题列表到顶部并刷新；从其他一级页面点击首页只执行导航。

相关设计：

- [`../plans/2026-06-29-navigation-layering-design.md`](../plans/2026-06-29-navigation-layering-design.md)
- [`../plans/2026-06-29-navigation-transition-design.md`](../plans/2026-06-29-navigation-transition-design.md)
- [`../plans/2026-07-12-v2ex-link-routing-design.md`](../plans/2026-07-12-v2ex-link-routing-design.md)

## 排障入口

UI 状态异常时检查 UiEvent 到 ViewModel 的处理、UseCase 返回、StateFlow 更新和生命周期收集；导航异常时检查目的地、根/内部 NavController、参数编码、返回栈与 `V2exLinkParser`。
