package app.mystery0.nodeflow.domain.auth

import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val session: Flow<AuthSession>
    suspend fun loginChallenge(): Result<LoginChallenge>
    suspend fun login(
        username: String,
        password: String,
        captcha: String,
        challenge: LoginChallenge,
    ): Result<AuthLoginResult>
    suspend fun verifyTwoFactor(
        code: String,
        challenge: TwoFactorChallenge,
    ): Result<AuthSession>
    suspend fun saveSession(session: AuthSession)
    suspend fun clearSession()
}
