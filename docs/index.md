# NodeFlow 文档索引

本文档是仓库详细资料的统一索引。AI Agent 应先阅读根目录 `AGENTS.md`，再根据任务类型打开这里列出的专题文档。

## 架构

- [总体架构](architecture/overview.md)：模块形态、分层、数据流、包职责和依赖注入。

## 开发规范

- [开发工作流](development/workflow.md)：编码、日志、隐私、Git、构建与交付要求。
- [测试与验证](development/testing.md)：单元测试范围、命令和真机验证要求。
- [文档规范](development/documentation.md)：目录分类、命名、维护和新文档落位规则。
- [GPT-6 Astra 指令与工作流审计](investigations/2026-09-12-agent-instructions-audit.md)：官方依据、问题清单、修改范围及验证限制。

## 子系统

- [网络、访问控制与登录](subsystems/network-auth.md)：V2EX 数据源、HTML 页面分类、Cookie、Token 和敏感信息。
- [数据库、缓存与设置](subsystems/storage.md)：Room、Migration、schema、缓存回退和 DataStore。
- [UI、导航与状态管理](subsystems/ui-navigation.md)：Compose、Material 3、UDF/MVI、导航与深链。
- [HTML、链接、图片与内容渲染](subsystems/content-rendering.md)：解析、富文本、应用内链接和图片预览。

## 待评审设计

- [图床适配模块与 Imgur 网页上传设计](plans/2026-09-24-image-hosting-adapters-design.md)：独立模块、可扩展适配器契约、V2EX 迁移、Imgur 匿名网页上传调研、草稿兼容与验收；尚未实施。

## 历史设计与计划

- [`plans/`](plans/)：已确认的设计说明和对应实施计划。设计与计划统一放在同一目录，通过文件名中的 `-design` 区分。
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
