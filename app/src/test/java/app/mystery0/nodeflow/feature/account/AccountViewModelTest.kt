package app.mystery0.nodeflow.feature.account

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.DailyCheckIn
import app.mystery0.nodeflow.core.model.DailyCheckInResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.model.UserRecentActivity
import app.mystery0.nodeflow.domain.account.AccountOverviewRepository
import app.mystery0.nodeflow.domain.account.CheckInUseCase
import app.mystery0.nodeflow.domain.account.GetAccountOverviewUseCase
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.notification.ObserveUnreadNotificationCountUseCase
import app.mystery0.nodeflow.domain.notification.UnreadNotificationRepository
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.testing.FakeAuthRepository
import app.mystery0.nodeflow.testing.FakeUserRepository
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.time.ZoneId
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

    @Test
    fun partialOverviewDoesNotErasePreviouslyLoadedCheckIn() = runTest(testDispatcher) {
        var requests = 0
        val repository = object : AccountOverviewRepository {
            override suspend fun overview() = Result.success(
                if (++requests == 1) AccountOverview(checkIn = DailyCheckIn(checkedIn = true))
                else AccountOverview(unreadNotificationCount = 2),
            )
            override suspend fun checkIn(): Result<DailyCheckInResult> = error("不应签到")
        }
        val auth = FakeAuthRepository(AuthSession(username = "tester", cookieHeader = "test-cookie"))
        val vm = createViewModel(auth, repository)
        advanceUntilIdle()
        vm.onEvent(AccountUiEvent.Refresh)
        advanceUntilIdle()
        assertThat(vm.uiState.value.overview?.checkIn?.checkedIn).isTrue()
    }

    @Test
    fun sharedActivityUpdatesWithoutReloadingOverviewAndRefreshStillLoads() = runTest(testDispatcher) {
        val activity = MutableStateFlow<Float?>(59f)
        var loads = 0
        val overview = object : AccountOverviewRepository {
            override suspend fun overview(): Result<AccountOverview> {
                loads++
                return Result.success(AccountOverview())
            }
            override suspend fun checkIn(): Result<DailyCheckInResult> = error("不应签到")
        }
        val auth = FakeAuthRepository(AuthSession(username = "reader", cookieHeader = "test-cookie"))
        val vm = AccountViewModel(
            ObserveAuthSessionUseCase(auth), GetUserProfileUseCase(FakeUserRepository()),
            GetAccountOverviewUseCase(overview), CheckInUseCase(overview), auth, unreadUseCase(),
            activityRepository = object : app.mystery0.nodeflow.domain.account.AccountActivityRepository {
                override val activityPercent: Flow<Float?> = activity
            },
        )
        advanceUntilIdle()
        assertThat(vm.uiState.value.activityPercent).isEqualTo(59f)
        val initialLoads = loads
        activity.value = 60f
        advanceUntilIdle()
        assertThat(vm.uiState.value.activityPercent).isEqualTo(60f)
        assertThat(loads).isEqualTo(initialLoads)
        vm.onEvent(AccountUiEvent.Refresh)
        advanceUntilIdle()
        assertThat(loads).isEqualTo(initialLoads + 1)
        activity.value = null
        advanceUntilIdle()
        assertThat(vm.uiState.value.activityPercent).isNull()
    }

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
            observeUnreadCount = unreadUseCase(),
        )

        advanceUntilIdle()

        assertThat(authRepository.clearSessionCalls).isEqualTo(1)
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
        assertThat(viewModel.uiState.value.user).isNull()
        assertThat(viewModel.uiState.value.overview).isNull()
        assertThat(viewModel.uiState.value.errorMessage).isEqualTo("登录状态已失效，请重新登录")
    }

    @Test
    fun switchingAccountClearsPreviousOverviewBeforeNewRequestCompletes() = runTest(testDispatcher) {
        var requests = 0
        val release = CompletableDeferred<Unit>()
        val repository = object : AccountOverviewRepository {
            override suspend fun overview(): Result<AccountOverview> {
                if (++requests > 1) release.await()
                return Result.success(AccountOverview(checkIn = DailyCheckIn(checkedIn = true)))
            }
            override suspend fun checkIn(): Result<DailyCheckInResult> = error("不应签到")
        }
        val auth = FakeAuthRepository(AuthSession(username = "first", cookieHeader = "test-first"))
        val vm = createViewModel(auth, repository)
        advanceUntilIdle()
        assertThat(vm.uiState.value.overview).isNotNull()

        auth.setSession(AuthSession(username = "second", cookieHeader = "test-second"))
        testDispatcher.scheduler.runCurrent()

        assertThat(vm.uiState.value.user).isNull()
        assertThat(vm.uiState.value.overview).isNull()
        assertThat(vm.uiState.value.isLoading).isTrue()
        release.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.uiState.value.user?.username).isEqualTo("second")
        assertThat(vm.uiState.value.isLoading).isFalse()
    }

    @Test
    fun loggingInSynchronouslySetsLoadingUntilOverviewCompletes() = runTest(testDispatcher) {
        val release = CompletableDeferred<Unit>()
        val repository = object : AccountOverviewRepository {
            override suspend fun overview(): Result<AccountOverview> {
                release.await()
                return Result.success(AccountOverview(unreadNotificationCount = 0))
            }
            override suspend fun checkIn(): Result<DailyCheckInResult> = error("不应签到")
        }
        val auth = FakeAuthRepository(AuthSession())
        val vm = createViewModel(auth, repository)
        advanceUntilIdle()
        assertThat(vm.uiState.value.isLoggedIn).isFalse()
        assertThat(vm.uiState.value.isLoading).isFalse()

        auth.setSession(AuthSession(username = "reader", cookieHeader = "test-cookie"))
        testDispatcher.scheduler.runCurrent()

        assertThat(vm.uiState.value.isLoggedIn).isTrue()
        assertThat(vm.uiState.value.isLoading).isTrue()
        release.complete(Unit)
        advanceUntilIdle()
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.overview?.unreadNotificationCount).isEqualTo(0)
    }

    @Test
    fun formatAccountJoinDate_usesLocalCalendarDateAndIgnoresMissing() {
        val timestamp = Instant.parse("2020-06-01T16:30:00Z").epochSecond
        assertThat(formatAccountJoinDate(timestamp, ZoneId.of("Asia/Shanghai"))).isEqualTo("2020-06-02")
        assertThat(formatAccountJoinDate(null)).isNull()
        assertThat(formatAccountJoinDate(0)).isNull()
    }

    @Test
    fun unreadUpdatesReachBadgeWithoutReloadingAccountOverview() = runTest(testDispatcher) {
        val counts = MutableStateFlow<Int?>(7)
        val repository = SuccessfulOverviewRepository(unreadNotificationCount = 99)
        val authRepository = FakeAuthRepository(AuthSession(cookieHeader = "test-cookie", username = "currentUser"))
        val viewModel = AccountViewModel(
            ObserveAuthSessionUseCase(authRepository), GetUserProfileUseCase(FakeUserRepository()),
            GetAccountOverviewUseCase(repository), CheckInUseCase(repository), authRepository,
            unreadUseCase(counts),
        )
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.unreadNotificationCount).isEqualTo(7)
        counts.value = 0
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.unreadNotificationCount).isEqualTo(0)
        viewModel.onEvent(AccountUiEvent.Refresh)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.unreadNotificationCount).isEqualTo(0)
    }

    private fun unreadUseCase(counts: Flow<Int?> = MutableStateFlow(null)) =
        ObserveUnreadNotificationCountUseCase(
            object : UnreadNotificationRepository {
                override val unreadCount = counts
            },
        )

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
            observeUnreadCount = unreadUseCase(),
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
            observeUnreadCount = unreadUseCase(),
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
            observeUnreadCount = unreadUseCase(),
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

    @Test
    fun refreshPreservesOverviewAndReportsPartialFailureWithoutRestartingRequest() = runTest(testDispatcher) {
        val release = CompletableDeferred<Unit>()
        var requests = 0
        val overview = AccountOverview(checkIn = DailyCheckIn(checkedIn = true))
        val repository = object : AccountOverviewRepository {
            override suspend fun overview(): Result<AccountOverview> {
                requests++
                if (requests == 1) return Result.success(overview)
                release.await()
                return Result.failure(NodeFlowException(NodeFlowException.Kind.Network, "刷新失败"))
            }
            override suspend fun checkIn(): Result<DailyCheckInResult> = error("不应签到")
        }
        val auth = FakeAuthRepository(AuthSession(username = "tester", cookieHeader = "test-cookie"))
        val vm = createViewModel(auth, repository)
        advanceUntilIdle()
        vm.onEvent(AccountUiEvent.Refresh)
        testDispatcher.scheduler.runCurrent()
        vm.onEvent(AccountUiEvent.Refresh)
        testDispatcher.scheduler.runCurrent()
        val requestsWhileRefreshing = requests
        val refreshingState = vm.uiState.value
        release.complete(Unit)
        advanceUntilIdle()
        assertThat(requestsWhileRefreshing).isEqualTo(2)
        assertThat(refreshingState.isLoading).isTrue()
        assertThat(refreshingState.user?.username).isEqualTo("tester")
        assertThat(vm.uiState.value.isLoading).isFalse()
        assertThat(vm.uiState.value.overview).isEqualTo(overview)
        assertThat(vm.uiState.value.errorMessage).isNotEmpty()
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
        observeUnreadCount = unreadUseCase(),
    )

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
