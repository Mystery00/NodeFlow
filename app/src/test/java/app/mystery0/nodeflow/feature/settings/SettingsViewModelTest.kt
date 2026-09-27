package app.mystery0.nodeflow.feature.settings

import android.content.ContextWrapper
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.ThemeMode
import app.mystery0.nodeflow.domain.membertag.MemberTagRepository
import app.mystery0.nodeflow.domain.membertag.ObserveMemberTagSyncedAtUseCase
import app.mystery0.nodeflow.domain.membertag.RefreshMemberTagsUseCase
import app.mystery0.nodeflow.domain.settings.ClearCacheUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import app.mystery0.nodeflow.imagehosting.contract.*
import app.mystery0.nodeflow.imagehosting.registry.DefaultImageHostRegistry
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
class SettingsViewModelTest {
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
    fun uiState_exposesRegistryDescriptorsWithoutProviderSpecificBranches() = runTest(testDispatcher) {
        val descriptor = ImageHostDescriptor(
            id = ImageHostId("custom"),
            displayName = "Custom host",
            capabilities = ImageHostCapabilities(emptySet(), 0),
            enabled = false,
        )
        val viewModel = createViewModel(
            imageHostRegistry = DefaultImageHostRegistry(listOf(object : ImageHostAdapter {
                override val descriptor = descriptor
                override suspend fun upload(image: UploadImage): UploadResult = UploadResult.Failure(
                    UploadFailure(
                        descriptor.id,
                        FailureCategory.InteractionRequired,
                        RequestStage.Preparation,
                        ResultCertainty.NotSubmitted,
                    ),
                )
            })),
        )

        assertThat(viewModel.uiState.value.imageHostDescriptors).containsExactly(descriptor)
    }

    @Test
    fun clearCache_failurePublishesFailureMessageAndStopsLoading() = runTest(testDispatcher) {
        val viewModel = createViewModel(clearCacheError = IllegalStateException("disk"))

        viewModel.onEvent(SettingsUiEvent.ClearCache)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isClearingCache).isFalse()
        assertThat(viewModel.uiState.value.message).isEqualTo("缓存清除失败")
    }

    @Test
    fun clearCache_cancellationRethrowsAndStopsLoading() = runTest(testDispatcher) {
        val viewModel = createViewModel(clearCacheError = CancellationException("cancelled"))
        advanceUntilIdle()
        val jobsBeforeClear = viewModel.viewModelScope.coroutineContext[Job]!!.children.toSet()

        viewModel.onEvent(SettingsUiEvent.ClearCache)
        val clearCacheJob = viewModel.viewModelScope.coroutineContext[Job]!!
            .children
            .first { it !in jobsBeforeClear }
        var completionCause: Throwable? = null
        clearCacheJob.invokeOnCompletion { completionCause = it }
        advanceUntilIdle()

        assertThat(completionCause).isInstanceOf(CancellationException::class.java)
        assertThat(viewModel.uiState.value.isClearingCache).isFalse()
        assertThat(viewModel.uiState.value.message).isNull()
    }

    @Test
    fun clearCache_successPublishesSuccessMessageAndStopsLoading() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onEvent(SettingsUiEvent.ClearCache)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isClearingCache).isFalse()
        assertThat(viewModel.uiState.value.message).isEqualTo("缓存已清除")
    }

    private fun createViewModel(
        clearCacheError: Throwable? = null,
        imageHostRegistry: DefaultImageHostRegistry = DefaultImageHostRegistry(emptyList()),
    ): SettingsViewModel {
        val settingsRepository = FakeSettingsRepository(clearCacheError)
        val memberTagRepository = FakeMemberTagRepository()
        return SettingsViewModel(
            observeSettings = ObserveSettingsUseCase(settingsRepository),
            observeMemberTagSyncedAt = ObserveMemberTagSyncedAtUseCase(memberTagRepository),
            updateSettings = UpdateSettingsUseCase(settingsRepository),
            clearCache = ClearCacheUseCase(settingsRepository),
            refreshMemberTags = RefreshMemberTagsUseCase(memberTagRepository),
            applicationContext = ContextWrapper(null),
            imageHostRegistry = imageHostRegistry,
        )
    }

    private class FakeSettingsRepository(
        private val clearCacheError: Throwable?,
    ) : SettingsRepository {
        override val settings: Flow<AppSettings> = MutableStateFlow(AppSettings())

        override suspend fun setThemeMode(themeMode: ThemeMode) = Unit

        override suspend fun setDynamicColor(enabled: Boolean) = Unit

        override suspend fun setPinnedHomeNode(node: PinnedHomeNode?) = Unit

        override suspend fun setCustomImageHosts(hosts: List<String>) = Unit

        override suspend fun setReplyImageHost(hostId: String) = Unit

        override suspend fun setShowMemberTags(enabled: Boolean) = Unit

        override suspend fun setNotificationReminder(enabled: Boolean) = Unit

        override suspend fun clearCache() {
            clearCacheError?.let { throw it }
        }
    }

    private class FakeMemberTagRepository : MemberTagRepository {
        override fun observeTags(): Flow<Map<String, List<String>>> = MutableStateFlow(emptyMap())

        override fun observeSyncedAt(): Flow<Long?> = MutableStateFlow(null)

        override suspend fun refresh(force: Boolean): Result<Unit> = Result.success(Unit)

        override suspend fun setTagsForUser(
            username: String,
            tags: List<String>,
            avatarUrl: String?,
        ): Result<Unit> = Result.success(Unit)
    }
}
