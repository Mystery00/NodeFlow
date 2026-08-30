package app.mystery0.nodeflow.data.auth

import app.mystery0.nodeflow.core.datastore.MemberTagStore
import app.mystery0.nodeflow.core.datastore.SessionStore
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.core.network.V2exCookieJar
import app.mystery0.nodeflow.domain.auth.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val sessionStore: SessionStore,
    private val webAuthRemoteDataSource: WebAuthRemoteDataSource,
    private val cookieJar: V2exCookieJar,
    private val memberTagStore: MemberTagStore,
    private val ioDispatcher: CoroutineDispatcher,
) : AuthRepository {
    override val session: Flow<AuthSession> = sessionStore.session

    override suspend fun loginChallenge(): Result<LoginChallenge> = withContext(ioDispatcher) {
        runCatching {
            webAuthRemoteDataSource.loginChallenge()
        }
    }

    override suspend fun login(
        username: String,
        password: String,
        captcha: String,
        challenge: LoginChallenge,
    ): Result<AuthLoginResult> = withContext(ioDispatcher) {
        runCatching {
            val result = webAuthRemoteDataSource.login(username, password, captcha, challenge)
            if (result is AuthLoginResult.Completed) {
                sessionStore.save(result.session)
            }
            result
        }
    }

    override suspend fun verifyTwoFactor(
        code: String,
        challenge: TwoFactorChallenge,
    ): Result<AuthSession> = withContext(ioDispatcher) {
        runCatching {
            val session = webAuthRemoteDataSource.verifyTwoFactor(code, challenge)
            sessionStore.save(session)
            session
        }
    }

    override suspend fun saveSession(session: AuthSession) = runAuthStorageMutation(ioDispatcher) {
        sessionStore.save(session)
    }

    override suspend fun clearSession() = runAuthStorageMutation(ioDispatcher) {
        cookieJar.clear()
        sessionStore.clear()
        // 标签数据属于账号私有内容，登出时一并清空
        memberTagStore.clear()
    }
}

internal suspend fun <T> runAuthStorageMutation(
    dispatcher: CoroutineDispatcher,
    block: suspend () -> T,
): T = withContext(dispatcher) { block() }
