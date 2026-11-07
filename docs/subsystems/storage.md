# 数据库、缓存与设置

## Room

数据库代码位于 `app/src/main/java/app/mystery0/nodeflow/core/database/`，包含 Database、DAO 和 Entity；schema 位于 `app/schemas/`。生产 Koin 模块使用 `Room.databaseBuilder` 创建 `nodeflow.db`。

当前完整结构定义为版本 1，包含主题、用户、节点/节点平面、回复草稿及图片记录，不保留开发阶段的 1～5 版本迁移历史。正常版本 1 数据库可继续使用；与当前 schema 不兼容的旧开发安装需要另行处理数据，不能要求所有用户日常升级都卸载重装。

- builder 不启用破坏性迁移，也没有注册历史 Migration。缺失迁移路径时拒绝打开数据库，不自动清库。
- `fallbackToDestructiveMigration(false)` 仍会允许删除重建 Room 管理的表；参数控制是否连同其他表一起删除，不能用于禁止破坏性迁移。
- 首次正式发布后，字段、索引、主键或表结构变更必须同步版本、显式 Migration 和导出 schema，并验证旧数据保留。
- `DatabaseMigrationPolicyTest` 在独立临时目录使用生产 DI 配置，验证版本 1 草稿重开后保留，以及模拟更高版本缺失降级迁移时拒绝打开并保留原数据。它不是尚未存在的未来升级路径测试。

## 缓存策略与边界

Repository 决定远端、本地缓存与回退策略，UI 不直接组合数据源。当前代码并非所有读取接口都使用同一缓存策略：

- 首页分页直接抓取远端并写入主题缓存；部分非分页主题、用户和节点读取支持本地优先或请求失败后回退缓存。
- 主题详情缓存只保存主题行、正文和渲染正文，不保存回复列表。详情首屏失败时，仅无回复主题等完整缓存可作为成功结果；不能据此承诺完整离线阅读。
- `TopicDetailPagerImpl` 对明确访问拒绝清理对应详情缓存，并用同主题请求共享的世代状态阻止旧结果覆盖拒绝状态；该机制不是账号级缓存隔离。
- 部分缓存写入仍处于请求成功链路中，写入异常可能导致请求失败；“非关键缓存失败不影响远端成功结果”是后续开发目标，不是当前全仓已实现保证。
- 设置中的“清除缓存”依次清理主题、节点和用户数据，不清理回复草稿、Coil 图片缓存、设置或登录态；清理失败会报告错误。

## 回复草稿

`reply_drafts` 以 `topicId` 为主键，保存正文、选区和更新时间；`reply_draft_images` 通过外键关联主题草稿，删除草稿时级联删除图片记录。图片 ID 使用 `<hostId>:<remoteId>` 命名空间，旧的无冒号值按 V2EX 兼容读取。

当前草稿没有账号字段，不按账号隔离；退出登录不会删除草稿。编辑器自动保存、成功后清理及上传行为见[UI 与导航](ui-navigation.md)，回复成功判定见[网络与认证](network-auth.md)。清理本地草稿不会删除图床上的远端图片。

## DataStore、安全存储与会话

普通设置及 Polish 标签缓存存于 `nodeflow_preferences` DataStore。Cookie、Personal Access Token 和用户名会话序列化后由 `EncryptedKeyValueStore` 加密落盘：Android Keystore 持有 AES-256 密钥，AES/GCM 每次写入生成随机 IV。生产 SharedPreferences 写入和删除使用同步提交，并检查提交结果。

- 加密 CookieStorage 保存 domain、path、expiry、persistent、secure、HttpOnly、host-only 和来源信息。Session 另外保存 Cookie header 快照，恢复路径的边界见[网络与认证](network-auth.md)。
- 密文损坏或无法解密时，安全存储尝试删除对应值并返回空值；删除本身失败仍可能抛错，不能承诺所有存储故障都自动恢复为未登录。
- `SessionStore.save/clear` 由同一 Mutex 串行化。登出在 Repository 中依次清理 CookieJar、SessionStore 和标签；这不是跨存储、网络请求和后台任务的原子事务，也没有统一会话世代阻止旧响应回写。
- 备份规则排除整个 `datastore/nodeflow_preferences.preferences_pb`、`nodeflow_secure_storage.xml` 和旧 Cookie 文件 `nodeflow_v2ex_cookies.xml`，因此普通设置和标签缓存也不参与云备份及设备迁移。
- Manifest 保持 `allowBackup=true`，Room 数据库没有在备份规则中排除。系统是否实际备份或迁移数据仍取决于系统条件和用户设置，不能表述为“全部本地数据均不备份”。

主要设置及默认值由 `SettingsStore` 定义：

| 设置 | 存储键 / 默认值 | 当前行为 |
| --- | --- | --- |
| 主题与动态色 | `theme_mode` / System；`dynamic_color` / true | 非法主题值回退跟随系统 |
| 首页固定节点 | `pinned_home_node_*` / 无 | 保存名称、标题和可选头像 |
| 自定义图片域名 | `custom_image_hosts` / 空 | 换行分隔，仅控制内容图片匹配，不决定上传 provider |
| 回复图片上传图床 | `reply_image_host` / `v2ex` | 保存稳定 ID；未知 ID 保留并提示重新选择，不静默回退 |
| Polish 标签展示 | `polish_member_tags_enabled` / true | 关闭只隐藏展示，不删除缓存 |
| 后台通知提醒 | `notification_reminder` / false | 关闭提醒会取消定时工作并清除未读数量基线 |

`MemberTagStore` 用 `polish_member_tags` 和 `polish_member_tags_synced_at` 保存标签映射与同步时间。`AppViewModel` 初始化时尝试刷新一次，1 小时 TTL 内跳过；设置页可强制同步，用户主页编辑成功后更新缓存。登出时清理标签，但进行中的远端刷新没有统一会话世代保护，不能将顺序清理描述为并发情况下的账号隔离保证。

## 排障入口

数据不一致时依次检查 Repository 策略、LocalDataSource、DAO 查询、映射、schema/版本、Dispatcher 以及缓存异常。迁移失败应补齐明确迁移或处理不兼容的开发数据，不恢复破坏性清库兜底。
