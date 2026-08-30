# 数据库、缓存与设置

## Room

数据库代码位于 `core.database`，包含 Database、DAO 和 Entity。导出的 schema 位于 `app/schemas/`。

NodeFlow 尚未发布，当前完整数据库结构被定义为版本 1，不保留开发阶段的历史 Migration；已有开发安装必须卸载后重装。首次正式发布后，任何数据库版本升级都必须提供显式 Migration 并更新 schema。

- 禁止使用破坏性迁移掩盖缺失 Migration；现有配置为 `fallbackToDestructiveMigration(false)`。
- 修改字段、索引、主键或表结构时，同时检查 DAO、映射、Migration（正式发布后）和测试。
- 数据库操作在合适 Dispatcher 上执行，不阻塞主线程。
- `topics.isPinned`、回复草稿和节点平面等当前能力均已包含在版本 1 基线中。

## 缓存策略

- Repository 统一决定远端优先、本地优先和缓存回退，UI 不组合数据源。
- 缓存用于改善加载和离线体验，不能把过期数据伪装成实时结果。
- 已明确的访问拒绝不能被旧缓存掩盖。
- 非关键缓存写入失败通常不应覆盖成功的网络结果。
- 清除缓存必须区分内容缓存、设置和登录态。

## DataStore、安全存储与会话

DataStore 保存普通用户设置。Cookie、Personal Access Token 和用户名会话不再写入 DataStore 或明文 SharedPreferences，而是序列化后通过 `EncryptedKeyValueStore` 加密落盘：密钥由 Android Keystore 持有，使用 AES-256/GCM，每次写入使用独立随机 IV。完整 Cookie 属性由加密 CookieStorage 保存。

安全存储读取遇到损坏密文、认证失败或不可解密数据时会删除对应值并按未登录处理，不记录密文或明文。会话保存和清除通过同一 Mutex 串行化，确保磁盘与内存 Flow 一致；登出同步清理 Cookie、Session 和账号私有标签。安全存储与旧 Cookie 文件均排除在云备份和设备迁移之外。

新增普通设置字段应明确默认值、升级兼容、清理方式和是否允许系统备份。Cookie 的具体约束参见[网络、访问控制与登录](network-auth.md)。

- 自定义图床域名列表存于 `custom_image_hosts`（换行分隔字符串），经 `AppSettings.customImageHosts` 暴露。
- Polish 用户标签缓存存于 `polish_member_tags`（用户名→标签列表的 JSON）与
  `polish_member_tags_synced_at`（秒级时间戳），由 `MemberTagStore` 管理；
  展示开关存于 `polish_member_tags_enabled`，经 `AppSettings.showMemberTags` 暴露。
  标签仅在应用启动（1 小时 TTL 内跳过）与设置页手动同步时拉取，浏览帖子只读本地缓存；
  退出登录时缓存随会话一并清除。用户主页编辑标签走读-改-写记事本的写路径，
  成功后以服务端回读结果更新本地缓存与同步时间。

## 排障入口

数据不一致时依次检查：Repository 数据策略、LocalDataSource、DAO 查询、Entity/领域模型映射、Migration、schema、Dispatcher 以及缓存失败是否被错误提升为主请求失败。
