# 数据库、缓存与设置

## Room

数据库代码位于 `core.database`，包含 Database、DAO、Entity 和 Migration。导出的 schema 位于 `app/schemas/`。

- 数据库版本升级必须提供显式 Migration 并更新 schema。
- 禁止使用破坏性迁移掩盖缺失 Migration；现有配置为 `fallbackToDestructiveMigration(false)`。
- 修改字段、索引、主键或表结构时，同时检查 DAO、映射、Migration 和测试。
- 数据库操作在合适 Dispatcher 上执行，不阻塞主线程。

## 缓存策略

- Repository 统一决定远端优先、本地优先和缓存回退，UI 不组合数据源。
- 缓存用于改善加载和离线体验，不能把过期数据伪装成实时结果。
- 已明确的访问拒绝不能被旧缓存掩盖。
- 非关键缓存写入失败通常不应覆盖成功的网络结果。
- 清除缓存必须区分内容缓存、设置和登录态。

## DataStore 与会话

DataStore 保存用户设置和会话相关状态。新增字段应明确默认值、升级兼容、清理方式和是否允许系统备份。Cookie 的具体约束参见[网络、访问控制与登录](network-auth.md)。

- 自定义图床域名列表存于 `custom_image_hosts`（换行分隔字符串），经 `AppSettings.customImageHosts` 暴露。

## 排障入口

数据不一致时依次检查：Repository 数据策略、LocalDataSource、DAO 查询、Entity/领域模型映射、Migration、schema、Dispatcher 以及缓存失败是否被错误提升为主请求失败。
