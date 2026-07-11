package app.mystery0.nodeflow.feature.account

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.model.UserRecentActivity
import app.mystery0.nodeflow.domain.account.AccountOverviewRepository
import app.mystery0.nodeflow.domain.account.GetAccountOverviewUseCase
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.domain.user.UserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadAccount_clearsSessionWhenOverviewReportsAuthExpired() = runTest(testDispatcher) {
        val authRepository = FakeAuthRepository(
            initialSession = AuthSession(
                cookieHeader = "expired-cookie",
                username = "Mystery0",
            ),
        )
        val viewModel = AccountViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(authRepository),
            getUserProfile = GetUserProfileUseCase(FakeUserRepository()),
            getAccountOverview = GetAccountOverviewUseCase(AuthExpiredOverviewRepository()),
            authRepository = authRepository,
        )

        advanceUntilIdle()

        assertThat(authRepository.clearSessionCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
        assertThat(viewModel.uiState.value.user).isNull()
        assertThat(viewModel.uiState.value.overview).isNull()
    }

    private class FakeAuthRepository(
        initialSession: AuthSession,
    ) : AuthRepository {
        private val sessionFlow = MutableStateFlow(initialSession)
        var clearSessionCalls = 0
            private set

        override val session: Flow<AuthSession> = sessionFlow

        override suspend fun loginChallenge(): Result<LoginChallenge> =
            error("不应在个人页加载测试中请求登录表单")

        override suspend fun login(
            username: String,
            password: String,
            captcha: String,
            challenge: LoginChallenge,
        ): Result<AuthLoginResult> = error("不应在个人页加载测试中登录")

        override suspend fun verifyTwoFactor(
            code: String,
            challenge: TwoFactorChallenge,
        ): Result<AuthSession> = error("不应在个人页加载测试中校验两步验证码")

        override suspend fun saveSession(session: AuthSession) {
            sessionFlow.value = session
        }

        override suspend fun clearSession() {
            clearSessionCalls += 1
            sessionFlow.value = AuthSession()
        }
    }

    private class FakeUserRepository : UserRepository {
        override suspend fun user(username: String, forceRefresh: Boolean): Result<User> =
            Result.success(User(username = username))

        override suspend fun recentActivity(username: String): Result<UserRecentActivity> =
            Result.success(UserRecentActivity())

        override suspend fun clearCache() = Unit
    }

    private class AuthExpiredOverviewRepository : AccountOverviewRepository {
        override suspend fun overview(): Result<AccountOverview> =
            Result.failure(
                NodeFlowException(
                    kind = NodeFlowException.Kind.Auth,
                    message = "登录状态已失效，请重新登录",
                ),
            )
    }
}
