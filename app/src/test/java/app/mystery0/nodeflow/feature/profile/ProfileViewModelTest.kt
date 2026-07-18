package app.mystery0.nodeflow.feature.profile

import androidx.lifecycle.SavedStateHandle
import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.model.UserRecentActivity
import app.mystery0.nodeflow.domain.membertag.MemberTagRepository
import app.mystery0.nodeflow.domain.membertag.ObserveMemberTagsUseCase
import app.mystery0.nodeflow.domain.membertag.UpdateMemberTagsForUserUseCase
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.domain.user.GetUserRecentActivityUseCase
import app.mystery0.nodeflow.domain.user.UserRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
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

    @Test
    fun editableTags_matchUsernameCaseInsensitively() = runTest(testDispatcher) {
        val memberTags = FakeMemberTagRepository(
            tags = mapOf("Alice" to listOf("大佬")),
        )
        val viewModel = viewModel(
            FakeUserRepository(userResult = Result.success(User(username = "alice"))),
            memberTags,
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.editableMemberTags).containsExactly("大佬")
    }

    @Test
    fun saveMemberTags_closesDialogOnSuccess() = runTest(testDispatcher) {
        val memberTags = FakeMemberTagRepository()
        val viewModel = viewModel(
            FakeUserRepository(userResult = Result.success(User(username = "alice"))),
            memberTags,
        )
        advanceUntilIdle()
        viewModel.onEvent(ProfileUiEvent.EditMemberTags)

        viewModel.onEvent(ProfileUiEvent.SaveMemberTags(listOf("大佬")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isTagDialogVisible).isFalse()
        assertThat(state.isSavingTags).isFalse()
        assertThat(state.tagEditError).isNull()
        assertThat(memberTags.lastSetTags).isEqualTo("alice" to listOf("大佬"))
    }

    @Test
    fun saveMemberTags_keepsDialogWithErrorOnFailure() = runTest(testDispatcher) {
        val memberTags = FakeMemberTagRepository(
            setResult = Result.failure(
                NodeFlowException(kind = NodeFlowException.Kind.Auth, message = "请先登录后再编辑标签"),
            ),
        )
        val viewModel = viewModel(
            FakeUserRepository(userResult = Result.success(User(username = "alice"))),
            memberTags,
        )
        advanceUntilIdle()
        viewModel.onEvent(ProfileUiEvent.EditMemberTags)

        viewModel.onEvent(ProfileUiEvent.SaveMemberTags(listOf("大佬")))
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertThat(state.isTagDialogVisible).isTrue()
        assertThat(state.tagEditError).isEqualTo("请先登录后再编辑标签")
    }

    private fun viewModel(
        repository: UserRepository,
        memberTags: FakeMemberTagRepository = FakeMemberTagRepository(),
    ): ProfileViewModel =
        ProfileViewModel(
            savedStateHandle = SavedStateHandle(mapOf("username" to "alice")),
            getUserProfile = GetUserProfileUseCase(repository),
            getUserRecentActivity = GetUserRecentActivityUseCase(repository),
            observeMemberTags = ObserveMemberTagsUseCase(memberTags),
            updateMemberTags = UpdateMemberTagsForUserUseCase(memberTags),
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

    private class FakeMemberTagRepository(
        tags: Map<String, List<String>> = emptyMap(),
        private val setResult: Result<Unit> = Result.success(Unit),
    ) : MemberTagRepository {
        private val state = MutableStateFlow(tags)

        var lastSetTags: Pair<String, List<String>>? = null
            private set

        override fun observeTags(): Flow<Map<String, List<String>>> = state

        override fun observeSyncedAt(): Flow<Long?> = state.map { null }

        override suspend fun refresh(force: Boolean): Result<Unit> = Result.success(Unit)

        override suspend fun setTagsForUser(
            username: String,
            tags: List<String>,
            avatarUrl: String?,
        ): Result<Unit> {
            lastSetTags = username to tags
            return setResult
        }
    }
}
