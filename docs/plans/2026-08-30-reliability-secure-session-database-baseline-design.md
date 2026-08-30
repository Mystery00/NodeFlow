# 可靠性、凭据加密与数据库基线重置设计

## 背景

NodeFlow 尚未对外发布，当前开发安装和本地数据可以直接丢弃。现有实现存在三组需要在继续开发前收口的问题：设置页缓存清理失败仍提示成功；后台通知绕过统一访问判断并可能周期性重复提醒；登录 Cookie 与 Personal Access Token 以明文形式保存在应用私有存储中。此外，Room 当前保留了尚无真实用户需要的 1～5 版本迁移历史。

## 目标

1. 缓存清理结果与用户提示一致。
2. 后台通知只处理可信的已登录首页响应，网络条件合适时执行，并避免同一未读数量反复提醒。
3. Cookie、PAT 和会话数据只以密文落盘，密钥由 Android Keystore 管理。
4. 当前完整 Room 结构重新定义为数据库版本 1，不保留历史 Migration。
5. 所有业务行为先通过失败测试锁定，再实现最小修复。

## 非目标

- 不接入 Android `AccountManager`。V2EX 不是 Android 系统账号提供方，项目没有同步适配器或跨应用账号共享需求；引入 Authenticator Service 不能替代 CookieJar 的加密持久化。
- 不使用 Credential Manager 保存 Session Cookie 或 PAT；Credential Manager 面向密码、Passkey 和联合登录交互，不是通用会话存储。
- 不迁移旧明文凭据、旧通知基线或旧数据库。开发设备需要卸载应用后重新安装并登录。
- 不增加新的通知页面、通知渠道或产品交互。

## 方案

### 设置缓存清理

`SettingsViewModel` 保持现有事件和 UI 状态模型，但根据 `ClearCacheUseCase` 的执行结果发布不同消息。无论成功或失败，`isClearingCache` 都必须恢复为 `false`；失败不得显示成功提示。

### 安全键值存储

新增职责单一的安全存储组件：

- `SecretCipher`：字符串加密与解密抽象。
- `AndroidKeystoreSecretCipher`：使用 `AndroidKeyStore` 中不可导出的 AES-256 密钥和 `AES/GCM/NoPadding`。每次加密生成 12 字节随机 IV，落盘载荷包含格式版本、IV 和密文。
- `StringKeyValueStorage`：最小字符串键值后端抽象。
- Android 后端使用应用私有 `SharedPreferences`，只接触密文。
- `EncryptedKeyValueStore`：组合后端和 Cipher。读取遇到损坏载荷、认证标签失败或密钥失效时删除对应值并返回空，不记录原文、密文或异常详情。

测试使用确定性的 Cipher 和内存后端，验证往返、落盘不含明文、删除以及损坏数据安全降级。生产代码不依赖已弃用的 Jetpack Security Crypto。

### 后台通知

后台检查拆为可测试的领域动作和 Android Worker 外壳：

1. 专用数据源请求 V2EX 首页，验证最终地址仍为可信 V2EX HTTPS 首页，并通过现有 Parser 区分已登录首页和匿名/登录失效页面。
2. 登录失效返回认证错误；响应、解析或网络异常保留现有异常分类，不把异常页面当作 0 条通知。
3. `NotificationReminderChecker` 读取加密保存的上次未读数量：
   - 当前数量为 0：保存 0，不通知。
   - 首次观察到正数：通知一次并保存该值。
   - 当前数量大于上次值：通知并保存新值。
   - 当前数量等于或小于上次正数：只更新基线，不通知。
4. Worker 只负责把 Checker 结果映射为系统通知和 WorkManager `Result`：成功或认证失效不重试，临时网络/服务异常重试。
5. 周期任务增加 `NetworkType.CONNECTED` 约束。关闭提醒时取消任务并清除已保存基线；重新开启后按首次观察规则工作。

### 凭据存储

- `SessionStore` 不再把 `personalAccessToken`、`cookieHeader` 或用户名写入 Preferences DataStore，而是将完整 `AuthSession` 序列化后写入安全键值存储，并通过内存 `StateFlow` 暴露当前会话。
- `V2exCookieStorage` 将完整 `StoredCookie` 列表序列化后写入同一安全存储的独立键，保留 domain、path、expiry、secure 等 Cookie 语义。
- 登出继续依次清理 CookieJar、SessionStore 和账号私有标签数据；安全存储删除后，内存会话立即变为空会话。
- 不读取旧的 `nodeFlowDataStore` 明文会话字段和 `nodeflow_v2ex_cookies` 明文 Cookie；旧文件由卸载应用清理。
- 备份和设备迁移规则排除安全存储文件；普通设置仍可按现有规则备份。

### Room 基线

- `NodeFlowDatabase.version` 设置为 `1`，实体保持当前完整结构。
- `DatabaseModule` 不注册任何 Migration。
- 删除历史 Migration 实现、Migration Android 测试及旧 schema。
- 通过 Room/KSP 重新生成唯一的 `app/schemas/.../1.json`，其结构必须等价于当前版本 5 schema 的最终结构，仅版本号和 identity hash 按新基线生成。

## 测试与验证

- JVM：设置 ViewModel、通知检查策略、安全存储、SessionStore、CookieStorage、Koin DI。
- 网络：使用 MockWebServer 覆盖已登录首页、匿名首页、异常地址和服务错误。
- Room：构建时重新导出 schema；不保留 Migration 测试。
- 全量：`:app:testDebugUnitTest`、`:app:assembleDebug`、`:app:lintDebug`。
- 真机/模拟器：卸载旧应用后重装，完成登录、进程重启会话恢复、登出清理和通知重复抑制检查。若当前环境无法执行，将明确记录剩余风险。
