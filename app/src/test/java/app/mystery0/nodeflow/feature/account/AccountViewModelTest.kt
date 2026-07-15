package app.mystery0.nodeflow.feature.account

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.DailyCheckIn
import app.mystery0.nodeflow.core.model.DailyCheckInResult
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.model.UserRecentActivity
import app.mystery0.nodeflow.domain.account.AccountOverviewRepository
import app.mystery0.nodeflow.domain.account.CheckInUseCase
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
import kotlinx.coroutines.CompletableDeferred
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
            checkIn = CheckInUseCase(AuthExpiredOverviewRepository()),
            authRepository = authRepository,
        )

        advanceUntilIdle()

        assertThat(authRepository.clearSessionCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
        assertThat(viewModel.uiState.value.user).isNull()
        assertThat(viewModel.uiState.value.overview).isNull()
    }

    @Test
    fun notificationsOpened_clearsDisplayedUnreadBadge() = runTest(testDispatcher) {
        val repository = SuccessfulOverviewRepository(unreadNotificationCount = 7)
        val authRepository = FakeAuthRepository(
            AuthSession(cookieHeader = "test-cookie", username = "currentUser"),
        )
        val viewModel = AccountViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(authRepository),
            getUserProfile = GetUserProfileUseCase(FakeUserRepository()),
            getAccountOverview = GetAccountOverviewUseCase(repository),
            checkIn = CheckInUseCase(repository),
            authRepository = authRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.NotificationsOpened)

        assertThat(viewModel.uiState.value.overview?.unreadNotificationCount).isEqualTo(0)
    }

    @Test
    fun checkIn_ignoresRepeatedClickAndPublishesRewardMessage() = runTest(testDispatcher) {
        val repository = SuccessfulOverviewRepository()
        val authRepository = FakeAuthRepository(
            AuthSession(cookieHeader = "test-cookie", username = "currentUser"),
        )
        val viewModel = AccountViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(authRepository),
            getUserProfile = GetUserProfileUseCase(FakeUserRepository()),
            getAccountOverview = GetAccountOverviewUseCase(repository),
            checkIn = CheckInUseCase(repository),
            authRepository = authRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.CheckIn)
        viewModel.onEvent(AccountUiEvent.CheckIn)
        testDispatcher.scheduler.runCurrent()

        assertThat(repository.checkInCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.isCheckingIn).isTrue()

        repository.releaseCheckIn.complete(Unit)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isCheckingIn).isFalse()
        assertThat(viewModel.uiState.value.toastMessage).isEqualTo("签到成功，获得 12 铜币")
    }

    @Test
    fun toastShown_consumesMessage() = runTest(testDispatcher) {
        val repository = SuccessfulOverviewRepository(releaseImmediately = true)
        val authRepository = FakeAuthRepository(
            AuthSession(cookieHeader = "test-cookie", username = "currentUser"),
        )
        val viewModel = AccountViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(authRepository),
            getUserProfile = GetUserProfileUseCase(FakeUserRepository()),
            getAccountOverview = GetAccountOverviewUseCase(repository),
            checkIn = CheckInUseCase(repository),
            authRepository = authRepository,
        )
        advanceUntilIdle()
        viewModel.onEvent(AccountUiEvent.CheckIn)
        advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.ToastShown)

        assertThat(viewModel.uiState.value.toastMessage).isNull()
    }

    @Test
    fun checkIn_usesGenericSuccessMessageWhenRewardCannotBeParsed() = runTest(testDispatcher) {
        val repository = SuccessfulOverviewRepository(releaseImmediately = true, rewardBronze = null)
        val authRepository = FakeAuthRepository(
            AuthSession(cookieHeader = "test-cookie", username = "currentUser"),
        )
        val viewModel = AccountViewModel(
            observeAuthSession = ObserveAuthSessionUseCase(authRepository),
            getUserProfile = GetUserProfileUseCase(FakeUserRepository()),
            getAccountOverview = GetAccountOverviewUseCase(repository),
            checkIn = CheckInUseCase(repository),
            authRepository = authRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.CheckIn)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.toastMessage).isEqualTo("签到成功")
    }

    @Test
    fun checkIn_failureResetsLoadingAndPublishesErrorMessage() = runTest(testDispatcher) {
        val repository = FailingCheckInRepository(
            NodeFlowException(NodeFlowException.Kind.Network, "签到网络失败"),
        )
        val authRepository = FakeAuthRepository(
            AuthSession(cookieHeader = "test-cookie", username = "currentUser"),
        )
        val viewModel = createViewModel(authRepository, repository)
        advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.CheckIn)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isCheckingIn).isFalse()
        assertThat(viewModel.uiState.value.toastMessage).isEqualTo("签到网络失败")
        assertThat(authRepository.clearSessionCalls).isEqualTo(0)
    }

    @Test
    fun checkIn_authFailureClearsSession() = runTest(testDispatcher) {
        val repository = FailingCheckInRepository(
            NodeFlowException(NodeFlowException.Kind.Auth, "登录状态已失效，请重新登录"),
        )
        val authRepository = FakeAuthRepository(
            AuthSession(cookieHeader = "test-cookie", username = "currentUser"),
        )
        val viewModel = createViewModel(authRepository, repository)
        advanceUntilIdle()

        viewModel.onEvent(AccountUiEvent.CheckIn)
        advanceUntilIdle()

        assertThat(authRepository.clearSessionCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
        assertThat(viewModel.uiState.value.isCheckingIn).isFalse()
    }

    private fun createViewModel(
        authRepository: AuthRepository,
        overviewRepository: AccountOverviewRepository,
    ) = AccountViewModel(
        observeAuthSession = ObserveAuthSessionUseCase(authRepository),
        getUserProfile = GetUserProfileUseCase(FakeUserRepository()),
        getAccountOverview = GetAccountOverviewUseCase(overviewRepository),
        checkIn = CheckInUseCase(overviewRepository),
        authRepository = authRepository,
    )

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

        override suspend fun checkIn(): Result<DailyCheckInResult> = overview().map {
            error("不应执行签到")
        }
    }

    private class SuccessfulOverviewRepository(
        releaseImmediately: Boolean = false,
        private val rewardBronze: Int? = 12,
        private val unreadNotificationCount: Int? = null,
    ) : AccountOverviewRepository {
        val releaseCheckIn = CompletableDeferred<Unit>().apply {
            if (releaseImmediately) complete(Unit)
        }
        var checkInCalls = 0
            private set
        private var checkedIn = false

        override suspend fun overview(): Result<AccountOverview> = Result.success(
            AccountOverview(
                unreadNotificationCount = unreadNotificationCount,
                checkIn = DailyCheckIn(
                    checkedIn = checkedIn,
                    canCheckIn = !checkedIn,
                ),
            ),
        )

        override suspend fun checkIn(): Result<DailyCheckInResult> {
            checkInCalls += 1
            releaseCheckIn.await()
            checkedIn = true
            return Result.success(
                DailyCheckInResult(
                    checkIn = DailyCheckIn(checkedIn = true),
                    rewardBronze = rewardBronze,
                ),
            )
        }
    }

    private class FailingCheckInRepository(
        private val error: Throwable,
    ) : AccountOverviewRepository {
        override suspend fun overview(): Result<AccountOverview> = Result.success(
            AccountOverview(checkIn = DailyCheckIn(checkedIn = false, canCheckIn = true)),
        )

        override suspend fun checkIn(): Result<DailyCheckInResult> = Result.failure(error)
    }
}
