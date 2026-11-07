# NodeFlow 文档索引

本文档是仓库详细资料的统一索引。AI Agent 应先阅读根目录 `AGENTS.md`，再根据任务类型打开这里列出的专题文档。当前行为以源码、构建配置和对应测试为依据；专题文档区分实现现状与开发约束，带日期的计划和调查不自动代表现状或验收通过。

## 架构

- [总体架构](architecture/overview.md)：模块形态、分层、数据流、包职责和依赖注入。

## 开发规范

- [开发工作流](development/workflow.md)：编码、日志、隐私、Git、构建与交付要求。
- [测试与验证](development/testing.md)：单元测试范围、命令和真机验证要求。
- [文档规范](development/documentation.md)：目录分类、命名、维护和新文档落位规则。
- [GPT-6 Astra 指令与工作流审计](investigations/2026-09-12-agent-instructions-audit.md)：官方依据、问题清单、修改范围及验证限制。
- [Imgur 匿名网页上传协议调查](investigations/2026-09-24-imgur-web-upload.md)：脱敏网页流程证据、原生 HTTP 实现边界和交互回退结论。

## 子系统

- [网络、访问控制与登录](subsystems/network-auth.md)：V2EX 数据源、HTML 页面分类、Cookie、Token 和敏感信息。
- [数据库、缓存与设置](subsystems/storage.md)：Room 版本 1、禁止破坏性迁移、草稿归属、缓存边界、DataStore 与实际备份范围。
- [UI、导航与状态管理](subsystems/ui-navigation.md)：Compose、Material 3、UDF/MVI、导航与深链。
- [HTML、链接、图片与内容渲染](subsystems/content-rendering.md)：解析、富文本、应用内链接和图片预览。

## 当前实现与历史方案的主要差异

| 主题 | 当前事实与入口 |
| --- | --- |
| 工程模块 | `:app` 与 `:image-hosting` 两个模块，见[总体架构](architecture/overview.md) |
| 数据库 | 完整结构统一为版本 1，没有历史 3→4/4→5 Migration；缺失迁移时拒绝打开，见[存储说明](subsystems/storage.md) |
| 回复提交 | POST 2xx 当前即视为成功，没有提交后按正文与作者确认；草稿按主题保存、不按账号隔离，见[网络说明](subsystems/network-auth.md) |
| 会话 | 加密存储已实现，但登出没有跨 Cookie、Session、缓存与网络响应的原子隔离，见[存储说明](subsystems/storage.md) |
| 图片展示 | Coil 3 与 ZoomImage Coil 3 适配模块；旧计划中的 Coil 2 版本不再适用，见[内容渲染](subsystems/content-rendering.md) |
| 置顶识别 | 仅使用 `corner_star.png` 等显式标记，没有时间异常推断或 60 秒容差，见[内容渲染](subsystems/content-rendering.md) |
| 图床上传 | `ImgurHttpImageHostAdapter` 已接入独立客户端；无整体 deadline，只有挑战类恢复动作提供网页入口，浏览器手动上传不自动回填，见[网络说明](subsystems/network-auth.md) |
| 消息入口 | 已迁入主页面底栏，早期个人页铃铛方案已替代，见[UI 与导航](subsystems/ui-navigation.md) |
| 发布流程 | 主分支预发布与 `v*` 正式发布分别实现；正式发布目前不归档 R8 mapping，见[工作流](development/workflow.md) |

当前回复图片使用 provider 命名空间 ID，并兼容旧 V2EX 图片 ID。代码已实现的协议路径、本地替身测试和真实站点验证应分别记录，不以计划中的待办勾选或浏览器观察替代当前验收。

## 历史设计与计划

- [`plans/`](plans/)：历史设计说明和实施计划，通过文件名中的 `-design` 区分；包含当时拟议、已实施及后来被替代的步骤，不能整体视为已完成或仍待执行。
- [图床适配模块与 Imgur 网页上传设计](plans/2026-09-24-image-hosting-adapters-design.md) / [实施计划](plans/2026-09-24-image-hosting-adapters.md)
- [可靠性、凭据加密与数据库基线重置设计](plans/2026-08-30-reliability-secure-session-database-baseline-design.md) / [实施计划](plans/2026-08-30-reliability-secure-session-database-baseline.md)
- [首页底部导航重复点击设计](plans/2026-07-15-home-bottom-bar-reselect-design.md) / [实施计划](plans/2026-07-15-home-bottom-bar-reselect.md)
- [首页重复点击展开 App Bar 设计](plans/2026-07-15-home-reselect-app-bar-design.md) / [实施计划](plans/2026-07-15-home-reselect-app-bar.md)
- [帖子详情节点 Chip 设计](plans/2026-07-15-topic-detail-node-chip-design.md) / [实施计划](plans/2026-07-15-topic-detail-node-chip.md)
- [V2EX 每日签到设计](plans/2026-07-15-v2ex-daily-check-in-design.md) / [实施计划](plans/2026-07-15-v2ex-daily-check-in.md)
- [通知中心设计](plans/2026-07-15-notification-center-design.md) / [实施计划](plans/2026-07-15-notification-center.md)
- [用户主页 404 友好占位设计](plans/2026-07-16-profile-user-not-found-design.md)
- [自定义图床域名设计](plans/2026-07-17-custom-image-host-design.md) / [实施计划](plans/2026-07-17-custom-image-host.md)
- [V2EX_Polish 用户标签兼容设计](plans/2026-07-17-polish-member-tag-design.md) / [实施计划](plans/2026-07-17-polish-member-tag.md)
- [V2EX_Polish 用户标签编辑设计](plans/2026-07-18-polish-member-tag-edit-design.md) / [实施计划](plans/2026-07-18-polish-member-tag-edit.md)
- [V2EX 创建回复设计](plans/2026-07-18-v2ex-create-reply-design.md) / [实施计划](plans/2026-07-18-v2ex-create-reply.md)
- [主题详情回复按需分页设计](plans/2026-07-18-topic-detail-reply-paging-design.md) / [实施计划](plans/2026-07-18-topic-detail-reply-paging.md)
- [“我的”签到提醒 Badge 设计](plans/2026-07-25-account-check-in-bottom-bar-badge-design.md) / [实施计划](plans/2026-07-25-account-check-in-bottom-bar-badge.md)
- [消息底部导航设计](plans/2026-07-25-notification-bottom-navigation-design.md) / [实施计划](plans/2026-07-25-notification-bottom-navigation.md)
- [系统深浅色启动页设计](plans/2026-07-25-system-splash-theme-design.md) / [实施计划](plans/2026-07-25-system-splash-theme.md)
- [帖子回复作者导航设计](plans/2026-08-14-topic-reply-author-navigation-design.md) / [实施计划](plans/2026-08-14-topic-reply-author-navigation.md)
- [节点详情屏蔽节点设计](plans/2026-08-14-node-blocking-design.md) / [实施计划](plans/2026-08-14-node-blocking.md)
- [帖子列表置顶标记设计](plans/2026-08-14-topic-pinned-badge-design.md) / [实施计划](plans/2026-08-14-topic-pinned-badge.md)
- [V2EX 主题与回复感谢支持实施计划](plans/2026-08-30-v2ex-thank-topic-reply.md)
- [主题正文原生富文本渲染设计](plans/2026-09-05-native-topic-rich-content-design.md) / [实施计划](plans/2026-09-05-native-topic-rich-content.md)
- [`investigations/`](investigations/)：问题调查、外部行为验证和技术研究记录。
- [V2EX_Polish 用户标签数据格式调查](investigations/2026-07-17-v2ex-polish-member-tag-format.md)
- [V2EX 主题与回复感谢接口调查](investigations/2026-08-30-v2ex-thank-topic-reply.md)

历史文档描述的是特定时间点的设计与实施背景。若其内容与当前源码、`AGENTS.md` 或专题文档冲突，以当前源码和现行规范为准。
