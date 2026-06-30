package app.mystery0.nodeflow.data.auth

import app.mystery0.nodeflow.core.datastore.SessionStore
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.domain.auth.AuthRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AuthRepositoryImpl(
    private val sessionStore: SessionStore,
    private val webAuthRemoteDataSource: WebAuthRemoteDataSource,
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

    override suspend fun saveSession(session: AuthSession) {
        sessionStore.save(session)
    }

    override suspend fun clearSession() {
        sessionStore.clear()
    }
}
