# NodeFlow 文档索引

本文档是仓库详细资料的统一索引。AI Agent 应先阅读根目录 `AGENTS.md`，再根据任务类型打开这里列出的专题文档。

## 架构

- [总体架构](architecture/overview.md)：模块形态、分层、数据流、包职责和依赖注入。

## 开发规范

- [开发工作流](development/workflow.md)：编码、日志、隐私、Git、构建与交付要求。
- [测试与验证](development/testing.md)：单元测试范围、命令和真机验证要求。
- [文档规范](development/documentation.md)：目录分类、命名、维护和新文档落位规则。

## 子系统

- [网络、访问控制与登录](subsystems/network-auth.md)：V2EX 数据源、HTML 页面分类、Cookie、Token 和敏感信息。
- [数据库、缓存与设置](subsystems/storage.md)：Room、Migration、schema、缓存回退和 DataStore。
- [UI、导航与状态管理](subsystems/ui-navigation.md)：Compose、Material 3、UDF/MVI、导航与深链。
- [HTML、链接、图片与内容渲染](subsystems/content-rendering.md)：解析、富文本、应用内链接和图片预览。

## 历史设计与计划

- [`plans/`](plans/)：已确认的设计说明和对应实施计划。设计与计划统一放在同一目录，通过文件名中的 `-design` 区分。
- [首页底部导航重复点击设计](plans/2026-07-15-home-bottom-bar-reselect-design.md) / [实施计划](plans/2026-07-15-home-bottom-bar-reselect.md)
- [首页重复点击展开 App Bar 设计](plans/2026-07-15-home-reselect-app-bar-design.md) / [实施计划](plans/2026-07-15-home-reselect-app-bar.md)
- [帖子详情节点 Chip 设计](plans/2026-07-15-topic-detail-node-chip-design.md) / [实施计划](plans/2026-07-15-topic-detail-node-chip.md)
- [V2EX 每日签到设计](plans/2026-07-15-v2ex-daily-check-in-design.md) / [实施计划](plans/2026-07-15-v2ex-daily-check-in.md)
- [`investigations/`](investigations/)：问题调查、外部行为验证和技术研究记录。

历史文档描述的是特定时间点的设计与实施背景。若其内容与当前源码、`AGENTS.md` 或专题文档冲突，以当前源码和现行规范为准。
