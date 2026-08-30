# 可靠性、凭据加密与数据库基线重置实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复缓存清理和后台通知问题，使用 Android Keystore 加密所有登录凭据，并把未发布的当前 Room 结构重置为版本 1。

**Architecture:** 先用测试锁定用户可见错误和通知策略，再建立可测试的加密键值存储，由通知基线、SessionStore 与 CookieStorage 共同复用。Room 不迁移历史数据，直接以当前实体结构重新导出版本 1 schema。

**Tech Stack:** Kotlin、Coroutines/Flow、WorkManager、Android Keystore AES-GCM、Preferences/SharedPreferences、Room/KSP、JUnit、Truth、MockWebServer。

**Spec:** `docs/plans/2026-08-30-reliability-secure-session-database-baseline-design.md`

## Global Constraints

- 与用户沟通、代码注释、KDoc 和项目文档使用中文；日志使用英文且不得包含凭据。
- 不迁移旧明文凭据、通知状态或旧数据库；开发设备通过卸载重装处理。
- 不使用 Android AccountManager、Credential Manager 或已弃用的 Jetpack Security Crypto 保存 Session Cookie/PAT。
- 加密密钥必须由 `AndroidKeyStore` 持有，算法为 AES-256/GCM/NoPadding，每次加密使用独立 12 字节随机 IV。
- 登录数据和安全存储文件不得参与云备份或设备迁移。
- 每项业务行为必须先写失败测试并观察预期失败，再写最小实现。
- 保留用户未提交的 `context.md`，不提交、不推送、不执行破坏性 Git 操作。

---

### Task 1: 修复缓存清理结果提示

**Files:**
- Create: `app/src/test/java/app/mystery0/nodeflow/feature/settings/SettingsViewModelTest.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/feature/settings/SettingsViewModel.kt`

**Interfaces:**
- Consumes: `ClearCacheUseCase` 当前 `suspend operator fun invoke()` 接口。
- Produces: `SettingsUiEvent.ClearCache` 成功显示“缓存已清除”，失败显示“缓存清除失败”，两者最终 `isClearingCache == false`。

- [ ] **Step 1: 写失败测试**

测试使用真实 `SettingsViewModel`、内存 Flow 和最小 Repository 替身，至少包含：

```kotlin
@Test
fun clearCache_failurePublishesFailureMessageAndStopsLoading() = runTest(testDispatcher) {
    val viewModel = createViewModel(clearCacheError = IllegalStateException("disk"))

    viewModel.onEvent(SettingsUiEvent.ClearCache)
    advanceUntilIdle()

    assertThat(viewModel.uiState.value.isClearingCache).isFalse()
    assertThat(viewModel.uiState.value.message).isEqualTo("缓存清除失败")
}
```

另写成功测试，断言消息为“缓存已清除”。

- [ ] **Step 2: 运行测试并确认 RED**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.feature.settings.SettingsViewModelTest"
```

Expected: 失败测试实际得到“缓存已清除”。

- [ ] **Step 3: 写最小实现**

将无条件提示改为：

```kotlin
val result = runCatching { clearCache() }
_uiState.update {
    it.copy(
        isClearingCache = false,
        message = if (result.isSuccess) "缓存已清除" else "缓存清除失败",
    )
}
```

- [ ] **Step 4: 运行局部测试并确认 GREEN**

执行 Task 1 的测试类，确认全部通过。

---

### Task 2: 建立安全键值存储并修复后台通知策略

**Files:**
- Create: `app/src/main/java/app/mystery0/nodeflow/core/security/SecretCipher.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/security/AndroidKeystoreSecretCipher.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/security/EncryptedKeyValueStore.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/security/SecurityModule.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/core/security/EncryptedKeyValueStoreTest.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/data/notification/NotificationReminderRemoteDataSource.kt`
- Create: `app/src/main/java/app/mystery0/nodeflow/core/notification/NotificationReminderChecker.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/data/notification/NotificationReminderRemoteDataSourceTest.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/core/notification/NotificationReminderCheckerTest.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/notification/NotificationCheckWorker.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/notification/NotificationScheduler.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/di/NodeFlowModules.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/data/DataSourceModule.kt`

**Interfaces:**
- Produces:

```kotlin
interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(payload: String): String
}

interface StringKeyValueStorage {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

class EncryptedKeyValueStore(
    private val storage: StringKeyValueStorage,
    private val cipher: SecretCipher,
) {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
}

data class NotificationReminderDecision(
    val unreadCount: Int,
    val shouldNotify: Boolean,
)
```

- `EncryptedKeyValueStore.read()` 解密失败时删除损坏值并返回 `null`。
- `NotificationReminderChecker.check()` 使用安全键 `notification_last_unread_count` 保存基线。

- [ ] **Step 1: 写安全存储失败测试**

覆盖：往返读取、后端值不含明文、删除、损坏密文被删除并返回空。测试使用内存后端与可逆测试 Cipher，不依赖 Android Framework。

- [ ] **Step 2: 运行安全存储测试并确认 RED**

Expected: 类型尚不存在导致编译失败。

- [ ] **Step 3: 实现最小安全存储与 Keystore Cipher**

`AndroidKeystoreSecretCipher` 使用：

```kotlin
private const val TRANSFORMATION = "AES/GCM/NoPadding"
private const val KEY_SIZE_BITS = 256
private const val IV_SIZE_BYTES = 12
private const val TAG_SIZE_BITS = 128
```

载荷格式为 `1:<base64Iv>:<base64CipherText>`。Android SharedPreferences 后端文件名固定为 `nodeflow_secure_storage`，只保存密文。

- [ ] **Step 4: 运行安全存储测试并确认 GREEN**

执行 `EncryptedKeyValueStoreTest`。

- [ ] **Step 5: 写通知数据源和决策失败测试**

数据源测试使用 MockWebServer，覆盖：

- 已登录首页未读数 3 → 返回 3。
- 已登录首页无未读 → 返回 0。
- 匿名首页或 `/signin` 最终地址 → `NodeFlowException.Kind.Auth`。
- 非成功响应 → 网络异常，不返回 0。

Checker 测试覆盖字面决策表：

| 上次 | 当前 | 通知 |
| --- | --- | --- |
| 无 | 3 | 是 |
| 3 | 3 | 否 |
| 3 | 5 | 是 |
| 5 | 2 | 否 |
| 2 | 0 | 否，并保存 0 |
| 0 | 2 | 是 |

- [ ] **Step 6: 运行通知测试并确认 RED**

执行两个新增测试类，确认因组件缺失而失败。

- [ ] **Step 7: 实现通知数据源、Checker、Worker 与调度**

- Worker 注入 Checker，不再直接注入 `V2exRawApi` 和 Parser。
- 认证失效映射为 `Result.success()`，临时异常映射为 `Result.retry()`，取消异常继续抛出。
- 只有 `shouldNotify` 为真时调用系统通知。
- WorkRequest 增加：

```kotlin
setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build(),
)
```

- 关闭提醒时取消 Work 并删除通知基线。

- [ ] **Step 8: 运行 Task 2 局部测试与 Koin 测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.security.*" --tests "app.mystery0.nodeflow.core.notification.*" --tests "app.mystery0.nodeflow.data.notification.NotificationReminderRemoteDataSourceTest" --tests "app.mystery0.nodeflow.di.NodeFlowKoinModuleTest"
```

---

### Task 3: 将 Session 与 Cookie 切换到加密存储

**Files:**
- Create: `app/src/test/java/app/mystery0/nodeflow/core/datastore/SessionStoreTest.kt`
- Create: `app/src/test/java/app/mystery0/nodeflow/core/network/EncryptedV2exCookieStorageTest.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/datastore/SessionStore.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/datastore/DataStoreModule.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/network/V2exCookieJar.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/network/NetworkModule.kt`
- Modify: `app/src/test/java/app/mystery0/nodeflow/core/network/V2exCookieJarTest.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`

**Interfaces:**
- `SessionStore` 构造参数改为 `EncryptedKeyValueStore`，公开接口保持：

```kotlin
val session: Flow<AuthSession>
suspend fun save(session: AuthSession)
suspend fun clear()
```

- 会话安全键固定为 `auth_session`。
- Cookie 安全键固定为 `v2ex_cookies`。
- 会话 JSON 字段为 `personalAccessToken`、`cookieHeader`、`username`；空会话通过删除键表示。

- [ ] **Step 1: 写 SessionStore 失败测试**

覆盖保存后新实例可恢复完整会话、清除立即发布空会话、损坏载荷按未登录处理；断言底层存储不出现 PAT 和 Cookie 明文。

- [ ] **Step 2: 写 CookieStorage 失败测试**

覆盖完整 Cookie 列表往返、底层密文不包含 Cookie 名和值、清除和损坏载荷降级为空列表。

- [ ] **Step 3: 运行新增测试并确认 RED**

Expected: 现有构造方式和明文 SharedPreferences 实现不满足测试。

- [ ] **Step 4: 实现加密 SessionStore 与 CookieStorage**

- 使用 kotlinx.serialization 的私有持久化 DTO，不能让 UI 或 Domain 接触密文格式。
- `SessionStore` 使用 `MutableStateFlow` 保存解密后的内存状态；写入成功后更新 Flow，清除时先删除安全键再发布空会话。
- 删除 `SessionStore` 对 `Context.nodeFlowDataStore` 的凭据读写。
- 用 `EncryptedV2exCookieStorage` 替换 `SharedPreferencesV2exCookieStorage`。
- 保持 `V2exCookieJar` 的 Cookie 匹配、过期和清除语义不变。

- [ ] **Step 5: 更新备份排除规则**

同时排除：

```xml
<exclude domain="sharedpref" path="nodeflow_secure_storage.xml" />
<exclude domain="sharedpref" path="nodeflow_v2ex_cookies.xml" />
```

若旧 Cookie 文件不再使用，仍保留排除项以避免开发残留参与备份。

- [ ] **Step 6: 运行 Session、Cookie、认证与 DI 测试**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.datastore.SessionStoreTest" --tests "app.mystery0.nodeflow.core.network.*Cookie*" --tests "app.mystery0.nodeflow.data.auth.*" --tests "app.mystery0.nodeflow.di.NodeFlowKoinModuleTest"
```

---

### Task 4: 将当前 Room 结构重置为版本 1

**Files:**
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/database/NodeFlowDatabase.kt`
- Modify: `app/src/main/java/app/mystery0/nodeflow/core/database/DatabaseModule.kt`
- Delete: `app/src/androidTest/java/app/mystery0/nodeflow/core/database/ReplyDraftMigrationTest.kt`
- Delete: `app/src/androidTest/java/app/mystery0/nodeflow/core/database/TopicPinnedMigrationTest.kt`
- Replace directory contents: `app/schemas/app.mystery0.nodeflow.core.database.NodeFlowDatabase/`

**Interfaces:**
- `NodeFlowDatabase.version == 1`。
- Room builder 不调用 `addMigrations`，也不启用破坏性迁移。
- 唯一 schema 文件为 `1.json`，包含当前全部 7 个实体和当前索引/外键。

- [ ] **Step 1: 删除 Migration 测试和旧 schema 文件**

删除两个仅验证历史升级的 Android 测试，以及 schema 目录下旧的 1～5 JSON；这一步不删除任何 Entity/DAO 测试。

- [ ] **Step 2: 设置当前数据库版本为 1 并移除 Migration**

将 `NodeFlowDatabase` 的 `version = 1`；删除 `DatabaseModule.kt` 中 Migration import、`addMigrations(...)` 和全部 Migration 对象。

- [ ] **Step 3: 通过 KSP 重新导出 schema**

Run:

```powershell
.\gradlew.bat :app:kspDebugKotlin
```

Expected: 生成 `app/schemas/app.mystery0.nodeflow.core.database.NodeFlowDatabase/1.json`。

- [ ] **Step 4: 检查 schema**

确认目录中只有 `1.json`，数据库版本为 1，包含 `topics.isPinned`、`reply_drafts`、`reply_draft_images`、节点平面表及全部当前实体。

- [ ] **Step 5: 运行数据库实体测试和 Debug 构建**

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "app.mystery0.nodeflow.core.database.*" :app:assembleDebug
```

---

### Task 5: 更新文档并完成全量验证

**Files:**
- Modify: `docs/subsystems/network-auth.md`
- Modify: `docs/subsystems/storage.md`
- Modify: `docs/development/testing.md`
- Modify: `docs/index.md`
- Modify: `docs/plans/2026-08-30-reliability-secure-session-database-baseline-design.md`（仅在实现事实与设计存在已裁决差异时）

**Interfaces:**
- 文档明确当前数据库从版本 1 起步、无 Migration 历史。
- 文档明确 Keystore 加密、不可备份、解密失败视为登出。
- 文档明确通知去重和认证失效策略。

- [ ] **Step 1: 更新专题文档和索引**

记录最终实现，不复制大段计划内容；`docs/index.md` 添加本设计和实施计划链接。

- [ ] **Step 2: 运行全量 JVM 测试**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL，全部测试通过。

- [ ] **Step 3: 运行 Debug 构建和 Lint**

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug
```

Expected: BUILD SUCCESSFUL；检查新增 Warning，不要求顺手修复与本计划无关的既有 Warning。

- [ ] **Step 4: 检查安全和工作区状态**

- 搜索 `personal_access_token`、`cookie_header`、`nodeflow_v2ex_cookies`，确认生产代码不再把凭据写入明文存储。
- 检查备份规则包含安全文件排除。
- 检查 Git diff 不包含真实凭据、构建产物或对 `context.md` 的修改。
- 记录未执行的模拟器验证：卸载、重装、登录、进程重启恢复、登出清理、重复通知抑制。
