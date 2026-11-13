package app.mystery0.nodeflow.testing

import androidx.paging.PagingData
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.NodePlane
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.ThemeMode
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.node.NodeRepository
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/** 测试用可配置认证仓储替身。 */
open class FakeAuthRepository(initialSession: AuthSession = AuthSession()) : AuthRepository {
    val sessionState = MutableStateFlow(initialSession)
    var challengeCalls = 0
    var loginCalls = 0
    var verificationCalls = 0
    var clearSessionCalls = 0
    var lastVerificationCode: String? = null
    var challengeResult: suspend () -> Result<LoginChallenge> = { Result.failure(AssertionError("Unexpected challenge")) }
    var loginResult: suspend (String, String, String, LoginChallenge) -> Result<AuthLoginResult> =
        { _, _, _, _ -> Result.failure(AssertionError("Unexpected login")) }
    var verifyTwoFactorResult: suspend (String, TwoFactorChallenge) -> Result<AuthSession> =
        { _, _ -> Result.failure(AssertionError("Unexpected 2FA")) }

    override val session: Flow<AuthSession> = sessionState

    override suspend fun loginChallenge(): Result<LoginChallenge> {
        challengeCalls++
        return challengeResult()
    }

    override suspend fun login(
        username: String,
        password: String,
        captcha: String,
        challenge: LoginChallenge,
    ): Result<AuthLoginResult> {
        loginCalls++
        return loginResult(username, password, captcha, challenge)
    }

    override suspend fun verifyTwoFactor(code: String, challenge: TwoFactorChallenge): Result<AuthSession> {
        verificationCalls++
        lastVerificationCode = code
        return verifyTwoFactorResult(code, challenge)
    }

    suspend fun setSession(session: AuthSession) {
        sessionState.value = session
    }

    override suspend fun clearSession() {
        clearSessionCalls++
        sessionState.value = AuthSession()
    }
}

/** 测试用内存设置仓储替身。 */
open class FakeSettingsRepository(initialSettings: AppSettings = AppSettings()) : SettingsRepository {
    val settingsState = MutableStateFlow(initialSettings)
    var clearCacheCalls = 0

    override val settings: Flow<AppSettings> = settingsState

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        settingsState.update { it.copy(themeMode = themeMode) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        settingsState.update { it.copy(dynamicColor = enabled) }
    }

    override suspend fun setPinnedHomeNode(node: PinnedHomeNode?) {
        settingsState.update { it.copy(pinnedHomeNode = node) }
    }

    override suspend fun setCustomImageHosts(hosts: List<String>) {
        settingsState.update { it.copy(customImageHosts = hosts) }
    }

    override suspend fun setReplyImageHost(hostId: String) {
        settingsState.update { it.copy(replyImageHost = hostId) }
    }

    override suspend fun setShowMemberTags(enabled: Boolean) {
        settingsState.update { it.copy(showMemberTags = enabled) }
    }

    override suspend fun setNotificationReminder(enabled: Boolean) {
        settingsState.update { it.copy(notificationReminder = enabled) }
    }

    override suspend fun clearCache() {
        clearCacheCalls++
    }
}

/** 测试用节点仓储替身。 */
open class FakeNodeRepository : NodeRepository {
    var planesResult: Result<List<NodePlane>> = Result.success(emptyList())
    var nodeResult: (String, Boolean) -> Result<Node> = { _, _ -> Result.failure(AssertionError("Unexpected node request")) }
    var pagingFactory: (String) -> Flow<PagingData<Topic>> = { flowOf(PagingData.empty()) }
    var blockResult: (String) -> Result<Unit> = { Result.success(Unit) }
    val blockedNames = mutableListOf<String>()
    var clearCacheCalls = 0

    override suspend fun nodePlanes(forceRefresh: Boolean): Result<List<NodePlane>> = planesResult
    override suspend fun node(name: String, forceRefresh: Boolean): Result<Node> = nodeResult(name, forceRefresh)
    override fun topicsPaging(name: String): Flow<PagingData<Topic>> = pagingFactory(name)
    override suspend fun blockNode(name: String): Result<Unit> {
        blockedNames += name
        return blockResult(name)
    }

    override suspend fun clearCache() {
        clearCacheCalls++
    }
}
