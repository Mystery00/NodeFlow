# 今日活跃度实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 从帖子列表和详情已有响应提取登录账号今日活跃度，全局共享并展示在“我的”财富卡片顶部。

**Architecture:** 新增领域只读活跃度 Repository 和内存 Store，参照 UnreadNotificationStore 的会话世代与请求序号隔离。数据源在可信且成功识别的页面响应中解析并更新；AccountViewModel 订阅领域 Flow，卡片仅展示共享值。手动刷新复用账号概览首页请求，不新增独立活跃度请求。

**Tech Stack:** Kotlin、Flow、Koin、Jsoup、Compose Material 3、JUnit、MockWebServer。

**Spec:** 本文“已确认设计”，来源为本次用户对话。

## 已确认设计

- 卡片第一行使用“今日活跃度：59%”格式，下一行为横向确定型进度条，位于该文案与“账户财富”之间，左右与卡片内容对齐，作为视觉分隔。
- 保留现有财富、签到、收藏主题、设置与退出布局；采用现有 Material 3 配色与字体。
- 从首页/最近主题、节点主题、收藏主题和帖子详情的已有网络 HTML 提取，每次有效响应更新全局值；不额外发请求。
- “我的”消费共享值；原有账户资料/财富/签到请求保留，手动刷新通过原有首页请求强制获取活跃度。首页默认移动模板若不含进度条，账号概览首页请求明确采用既有桌面 UA，其他首页调用默认行为不变。
- 已实测桌面首页结构为 `.member-activity-bar` 下带 `member-activity-*` class 的子节点，其 `width` 为百分比（已观察到 `member-activity-start` 的 59% 与 `member-activity-fourth` 的 99%）；不可固定一种阶段 class。账号卡片通过会员链接及三类收藏入口识别，不能假设桌面页含退出链接。百分比是活跃度，不是 User.dailyActivityRank。
- 比较过独立卡片与现有卡片内显示，用户选择卡片内分隔条样式。

## Global Constraints

- 禁止卸载模拟器现有 App、清数据或退出登录；安装使用覆盖安装，保留签名与包名。
- UI/ViewModel 不访问网络或存储；依赖通过 Koin 注入，中文文案使用资源。
- 不记录 Cookie、token、once、完整登录 HTML 或个人资料；自动化网络回归使用替身。
- 缺失或非法值保持未知，不能当作 0；刷新缺失保留同会话旧值；登出/切换会话立即失效。
- 不持久化活跃度、不新增数据库结构、依赖、权限、定时器或请求。
- 不提交、推送或创建 PR；Gradle 同一目录串行执行。

## Review Focus

- 伪造正文中的进度条、登录/挑战页、站外或错误路径响应不可更新共享状态。
- 0% 和 100% 有效；负数、越界、非百分比、无穷值与缺失不可变为有效进度。
- 账号切换、登出和旧会话请求完成后不能暴露旧账号活跃度。
- 较早请求晚返回不能覆盖较新有效响应；缺失值不能抹掉已知有效值。
- 仅有活跃度而无财富/签到时卡片仍可展示，大字体和深色模式语义可读。

## 实施步骤

### 1. 解析与共享状态

**Files:** `core/parser/V2exHtmlParser.kt`、新增 `domain/account/AccountActivityRepository.kt` 和 `data/account/AccountActivityStore.kt`；对应 parser/store 单测。

**Interfaces:** `parseAccountActivityPercent(html: String): Float?`；Repository 暴露 `Flow<Float?>`；Store 提供会话绑定 `beginRequest()` 与 `update(token, percent)`，与未读数 Store 保持一致的排序和会话语义。

- [x] 添加范围/小数/空白/无关正文/异常页面解析测试及共享状态会话与乱序测试，运行并确认失败。
- [x] 实现专用侧栏节点解析，仅接受有限 0..100 百分比；实现会话隔离 Store 与领域订阅接口。
- [x] 运行新增聚焦测试并确认通过。

### 2. 接入已有响应与“我的”订阅

**Files:** `data/topic/TopicRemoteDataSource.kt`、`data/node/NodeRemoteDataSource.kt`、`data/account/AccountRemoteDataSource.kt`、`core/network/V2exRawApi.kt`、`data/DataSourceModule.kt`、`feature/FeatureModule.kt`、`feature/account/AccountViewModel.kt`、`AccountUiState.kt` 及相关测试。

**Interfaces:** 数据源消费可选 Store（沿用已有测试构造惯例）；ViewModel 消费领域 Repository 的 Flow，写入 `AccountUiState.activityPercent`。

- [x] 添加已有请求顺带更新、无额外请求、匿名/挑战/错误来源/缓存拒绝更新和强制刷新回归；确认失败。
- [x] 在受信页面解析成功后更新 Store，开始请求时绑定 token，缓存响应不覆盖最新网络值；账号概览使用已有首页请求的桌面模板。
- [x] 注册 Koin 单例与领域绑定，订阅共享值；不把概览旧值回写为活跃度。
- [x] 运行数据源与 AccountViewModel 聚焦回归并确认通过。

### 3. 卡片展示与验收

**Files:** `feature/account/AccountOverviewCard.kt`、`AccountScreen.kt`、`app/src/main/res/values/strings.xml`、相关 UI 测试；现行 `docs/subsystems/network-auth.md`、`ui-navigation.md`。

- [x] 添加活跃度文案、缺失隐藏、仅活跃度可展示和进度语义回归；确认失败。
- [x] 将活跃度文案与直线横向进度条放在财富上方，采用主题色与内容统一边距，处理字体缩放。
- [x] 更新现行说明，运行 `:app:testDebugUnitTest :app:assembleDebug`；UI 兼容性或 Lint 风险需要时运行 `:app:lintDebug`。
- [x] 覆盖安装同签名 Debug APK，真实检查列表/详情更新、我的消费、手动刷新及登录状态保留；保存真实截图到 build 输出。
- [x] 尝试同一 agy_ui task `account-daily-activity` 视觉评审；若工具仍不可用，明确未完成独立评审，不伪造接受。（已尝试，工具受阻；独立视觉评审 NOT_VERIFIED。）

## 执行条件

设计已由用户明确，用户也已通过结构化选项批准“当前会话直接实施和验证”。本会话通过 SSH 操作 Windows，未继承 HERDR 环境，不能伪造或控制外部 Herdr pane。独立协同/视觉审查能力不可用须如实报告，无需重复审批布局。

## 完成记录

- 三项实现步骤已完成；新增解析、共享状态、原响应更新、ViewModel 订阅、UI 几何/语义和 Koin 接线回归。
- 最终 `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` 成功，592 项 JVM 测试零失败/零跳过，Lint 报告 No issues found。
- AccountScreenTest 7 项设备测试通过，覆盖新增进度语义/位置及深色窄屏 200% 字体。
- 真实 App 已覆盖安装，原登录态保留；操作首页列表、帖子详情、我的及刷新，当前服务端活跃度为 100%。浅色和深色实际截图保存在忽略的 `app/build/outputs/android-cli/`，系统夜间模式已恢复原设置。
- 已完成作者单独自审；独立代码审查未执行（本次用户已同意单会话验证）。agy_ui 同一任务两次调用均因 Mac Herdr 不可用而受阻，未声称独立视觉接受。
- 未提交、推送或创建 PR；临时真实服务结构探针已删除。
