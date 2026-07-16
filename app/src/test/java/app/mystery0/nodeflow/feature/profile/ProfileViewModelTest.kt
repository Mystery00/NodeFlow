package app.mystery0.nodeflow.feature.profile

import androidx.lifecycle.SavedStateHandle
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.model.UserRecentActivity
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.domain.user.GetUserRecentActivityUseCase
import app.mystery0.nodeflow.domain.user.UserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
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
    fun initialLoad_marksUserNotFoundOn404() = runTest(testDispatcher) {
        val repository = FakeUserRepository(
            userResult = Result.failure(notFound()),
        )
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isLoading).isFalse()
        assertThat(state.user).isNull()
        assertThat(state.userNotFound).isTrue()
    }

    @Test
    fun initialLoad_keepsRetryableErrorForOtherFailures() = runTest(testDispatcher) {
        val networkError = NodeFlowException(
            kind = NodeFlowException.Kind.Network,
            message = "网络连接失败，请稍后重试",
        )
        val repository = FakeUserRepository(
            userResult = Result.failure(networkError),
        )
        val viewModel = viewModel(repository)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.userNotFound).isFalse()
        assertThat(state.errorMessage).isEqualTo("网络连接失败，请稍后重试")
    }

    @Test
    fun refresh_clearsNotFoundWhenUserBecomesAvailable() = runTest(testDispatcher) {
        val repository = FakeUserRepository(
            userResult = Result.failure(notFound()),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertThat(viewModel.uiState.value.userNotFound).isTrue()

        repository.userResult = Result.success(User(username = "alice"))
        viewModel.onEvent(ProfileUiEvent.Refresh)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.userNotFound).isFalse()
        assertThat(state.user?.username).isEqualTo("alice")
        assertThat(state.errorMessage).isNull()
    }

    private fun viewModel(repository: UserRepository): ProfileViewModel =
        ProfileViewModel(
            savedStateHandle = SavedStateHandle(mapOf("username" to "alice")),
            getUserProfile = GetUserProfileUseCase(repository),
            getUserRecentActivity = GetUserRecentActivityUseCase(repository),
        )

    private fun notFound(): NodeFlowException =
        NodeFlowException(
            kind = NodeFlowException.Kind.NotFound,
            message = "请求失败：HTTP 404",
        )

    private class FakeUserRepository(
        var userResult: Result<User>,
    ) : UserRepository {
        override suspend fun user(username: String, forceRefresh: Boolean): Result<User> = userResult

        override suspend fun recentActivity(username: String): Result<UserRecentActivity> =
            Result.success(UserRecentActivity())

        override suspend fun clearCache() = Unit
    }
}
