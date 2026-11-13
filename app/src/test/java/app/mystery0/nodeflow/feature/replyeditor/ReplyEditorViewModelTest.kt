package app.mystery0.nodeflow.feature.replyeditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.reply.AddReplyDraftImageUseCase
import app.mystery0.nodeflow.domain.reply.ClearReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.CreateReplyResult
import app.mystery0.nodeflow.domain.reply.CreateReplyUseCase
import app.mystery0.nodeflow.domain.reply.GetReplyConstraintsUseCase
import app.mystery0.nodeflow.domain.reply.ImageUploadRepository
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.domain.reply.LoadReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.ReplyConstraints
import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.ReplyDraftRepository
import app.mystery0.nodeflow.domain.reply.ReplyRepository
import app.mystery0.nodeflow.domain.reply.SaveReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.UploadImageUseCase
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.imagehosting.contract.*
import app.mystery0.nodeflow.imagehosting.registry.DefaultImageHostRegistry
import app.mystery0.nodeflow.testing.FakeAuthRepository
import app.mystery0.nodeflow.testing.FakeSettingsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplyEditorViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun contentChange_savesDraftAfterDebounce() = runTest(dispatcher) {
        val drafts = FakeDraftRepository()
        val viewModel = viewModel(drafts)
        advanceUntilIdle()

        viewModel.onEvent(ReplyEditorUiEvent.ContentChanged(TextFieldValue("draft")))
        advanceTimeBy(399)
        assertThat(drafts.saved).isEmpty()
        advanceTimeBy(1)
        advanceUntilIdle()

        assertThat(drafts.saved.single().content).isEqualTo("draft")
    }

    @Test
    fun repeatedSubmit_startsOnlyOneCreateRequest() = runTest(dispatcher) {
        val drafts = FakeDraftRepository()
        val replies = FakeReplyRepository()
        val viewModel = viewModel(drafts, replies)
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ContentChanged(TextFieldValue("reply")))

        viewModel.onEvent(ReplyEditorUiEvent.Submit)
        viewModel.onEvent(ReplyEditorUiEvent.Submit)
        runCurrent()

        assertThat(replies.createCalls).isEqualTo(1)
    }

    @Test
    fun flushEmptyDraft_clearsStoredDraftInsteadOfSavingEmptyRow() = runTest(dispatcher) {
        val drafts = FakeDraftRepository()
        val viewModel = viewModel(drafts)
        advanceUntilIdle()

        viewModel.onEvent(ReplyEditorUiEvent.FlushDraft)
        advanceUntilIdle()

        assertThat(drafts.saved).isEmpty()
        assertThat(drafts.clearCalls).isEqualTo(1)
    }

    @Test
    fun delayedDraftLoad_doesNotOverwriteNewUserInput() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        val drafts = FakeDraftRepository(
            initialDraft = ReplyDraft(42, "旧草稿", 0, 0, emptyList(), 1),
            loadGate = gate,
        )
        val viewModel = viewModel(drafts)
        runCurrent()

        viewModel.onEvent(ReplyEditorUiEvent.ContentChanged(TextFieldValue("新输入")))
        gate.complete(Unit)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.value.text).isEqualTo("新输入")
    }

    @Test
    fun confirmedReply_remainsSuccessfulWhenLocalDraftCleanupFails() = runTest(dispatcher) {
        val drafts = FakeDraftRepository(throwOnClear = true)
        val viewModel = viewModel(drafts)
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ContentChanged(TextFieldValue("reply")))
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(ReplyEditorUiEvent.Submit)
        advanceUntilIdle()

        assertThat(effect.await()).isEqualTo(ReplyEditorEffect.ReplyCreated(1))
        assertThat(viewModel.uiState.value.message).isNull()
        assertThat(viewModel.uiState.value.isSubmitting).isFalse()
    }

    @Test
    fun authenticationFailure_requestsLoginAndKeepsDraft() = runTest(dispatcher) {
        val drafts = FakeDraftRepository()
        val replies = FakeReplyRepository(
            createResult = CreateReplyResult.Failure(
                app.mystery0.nodeflow.domain.reply.ReplyFailureReason.AuthenticationRequired,
                "登录已失效",
            ),
        )
        val viewModel = viewModel(drafts, replies)
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ContentChanged(TextFieldValue("reply")))
        val effect = async { viewModel.effects.first() }

        viewModel.onEvent(ReplyEditorUiEvent.Submit)
        advanceUntilIdle()

        assertThat(effect.await()).isEqualTo(ReplyEditorEffect.RequestLogin)
        assertThat(viewModel.uiState.value.value.text).isEqualTo("reply")
    }

    @Test
    fun imageSelected_forDisabledProvider_doesNotRequestLoginBeforeUpload() = runTest(dispatcher) {
        val uploads = mutableListOf<ImageHostId>()
        val viewModel = viewModel(
            authRepository = FakeAuthRepository(AuthSession()),
            settings = AppSettings(replyImageHost = "imgur"),
            imageRegistry = registry("imgur", enabled = false),
            imageRepository = object : ImageUploadRepository {

                override suspend fun upload(hostId: ImageHostId, contentUri: String): ImageUploadResult {
                    uploads += hostId
                    return ImageUploadResult.Failure(
                        ImageUploadFailureReason.UploadUnconfirmed,
                        "手动上传",
                        RecoveryAction.OpenHostPage("https://imgur.com/upload"),
                    )
                }
            },
        )
        advanceUntilIdle()

        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://image"))
        advanceUntilIdle()

        assertThat(uploads).containsExactly(ImageHostId("imgur"))
        assertThat(viewModel.uiState.value.showGalleryAction).isTrue()
    }

    @Test
    fun imageSelected_authenticationFailure_requestsLogin() = runTest(dispatcher) {
        val viewModel = viewModel(
            imageRepository = object : ImageUploadRepository {

                override suspend fun upload(hostId: ImageHostId, contentUri: String) =
                    ImageUploadResult.Failure(ImageUploadFailureReason.AuthenticationRequired, "登录已失效")
            },
        )
        advanceUntilIdle()
        val login = async { viewModel.effects.first() }
        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://image"))
        advanceUntilIdle()

        assertThat(login.await()).isEqualTo(ReplyEditorEffect.RequestLogin)
    }

    @Test
    fun galleryRequested_emitsSelectedHostAndRecoveryAction() = runTest(dispatcher) {
        val viewModel = viewModel(
            settings = AppSettings(replyImageHost = "imgur"),
            imageRegistry = registry("imgur", enabled = false),
            imageRepository = object : ImageUploadRepository {

                override suspend fun upload(hostId: ImageHostId, contentUri: String) =
                    ImageUploadResult.Failure(
                        ImageUploadFailureReason.UploadUnconfirmed,
                        "手动上传",
                        RecoveryAction.OpenHostPage("https://imgur.com/upload"),
                    )
            },
        )
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://image"))
        advanceUntilIdle()
        val effect = async { viewModel.effects.first() }
        viewModel.onEvent(ReplyEditorUiEvent.GalleryRequested)

        assertThat(effect.await()).isEqualTo(
            ReplyEditorEffect.OpenGallery(
                RecoveryAction.OpenHostPage("https://imgur.com/upload"),
            ),
        )
    }

    @Test
    fun providerSwitch_clearsPreviousRecoveryAction() = runTest(dispatcher) {
        val settingsRepository = FakeSettingsRepository(AppSettings(replyImageHost = "imgur"))
        val viewModel = viewModel(
            settingsRepository = settingsRepository,
            imageRegistry = registry("imgur", "v2ex", enabled = false),
            imageRepository = object : ImageUploadRepository {

                override suspend fun upload(hostId: ImageHostId, contentUri: String) =
                    ImageUploadResult.Failure(
                        ImageUploadFailureReason.UploadUnconfirmed,
                        "manual",
                        RecoveryAction.OpenHostPage("https://imgur.com/upload"),
                    )
            },
        )
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://image"))
        advanceUntilIdle()
        settingsRepository.settingsState.value = AppSettings(replyImageHost = "v2ex")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.recoveryAction).isEqualTo(RecoveryAction.None)
        assertThat(viewModel.uiState.value.showGalleryAction).isFalse()
    }

    @Test
    fun delayedUploadResult_survivesCloseAndReopenWhenRequestIsCurrent() = runTest(dispatcher) {
        val gate = CompletableDeferred<ImageUploadResult>()
        val image = UploadedReplyImage("imgur:id", "https://i.imgur.com/a.png", "https://imgur.com/a", "a.png", 1)
        val viewModel = viewModel(
            imageRepository = object : ImageUploadRepository {

                override suspend fun upload(hostId: ImageHostId, contentUri: String) = gate.await()
            },
        )
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://image"))
        runCurrent()
        viewModel.onEvent(ReplyEditorUiEvent.Close)
        viewModel.onEvent(ReplyEditorUiEvent.OpenTopicReply)
        gate.complete(ImageUploadResult.Success(image))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.value.text).contains(image.originalUrl)
    }

    @Test
    fun openFloorReply_duringUpload_doesNotModifyContentOrRequest() = runTest(dispatcher) {
        val gate = CompletableDeferred<ImageUploadResult>()
        val viewModel = viewModel(
            imageRepository = object : ImageUploadRepository {

                override suspend fun upload(hostId: ImageHostId, contentUri: String) = gate.await()
            },
        )
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ContentChanged(TextFieldValue("before")))
        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://image"))
        runCurrent()
        viewModel.onEvent(ReplyEditorUiEvent.OpenFloorReply("alice", 7))

        assertThat(viewModel.uiState.value.value.text).isEqualTo("before")
        assertThat(viewModel.uiState.value.isUploading).isTrue()
        gate.complete(ImageUploadResult.Failure(ImageUploadFailureReason.Network, "network"))
        advanceUntilIdle()
    }

    @Test
    fun providerSwitch_duringUpload_isIgnored() = runTest(dispatcher) {
        val gate = CompletableDeferred<ImageUploadResult>()
        val settingsRepository = FakeSettingsRepository(AppSettings())
        val viewModel = viewModel(
            settingsRepository = settingsRepository,
            imageRepository = object : ImageUploadRepository {

                override suspend fun upload(hostId: ImageHostId, contentUri: String) = gate.await()
            },
        )
        advanceUntilIdle()
        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://image"))
        runCurrent()
        settingsRepository.settingsState.value = AppSettings(replyImageHost = "imgur")

        assertThat(viewModel.uiState.value.currentImageHostId).isEqualTo("v2ex")
        gate.complete(ImageUploadResult.Failure(ImageUploadFailureReason.Network, "网络错误"))
        advanceUntilIdle()
    }

    @Test
    fun uploadException_releasesEditorLock() = runTest(dispatcher) {
        val imageRepository = object : ImageUploadRepository {
            override suspend fun upload(hostId: ImageHostId, contentUri: String): ImageUploadResult = error("network")
        }
        val viewModel = viewModel(FakeDraftRepository(), imageRepository = imageRepository)
        advanceUntilIdle()

        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://test/image"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.isUploading).isFalse()
        assertThat(viewModel.uiState.value.message).isNotNull()
    }

    @Test
    fun uploadedImage_remainsSuccessfulWhenLocalImageRecordFails() = runTest(dispatcher) {
        val image = UploadedReplyImage(
            "id",
            "https://i.v2ex.co/image.png",
            "https://www.v2ex.com/i/id",
            "image.png",
            1,
        )
        val imageRepository = object : ImageUploadRepository {
            override suspend fun upload(hostId: ImageHostId, contentUri: String) = ImageUploadResult.Success(image)
        }
        val viewModel = viewModel(
            FakeDraftRepository(throwOnAddImage = true),
            imageRepository = imageRepository,
        )
        advanceUntilIdle()

        viewModel.onEvent(ReplyEditorUiEvent.ImageSelected("content://test/image"))
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.value.text).contains(image.originalUrl)
        assertThat(viewModel.uiState.value.message).isEqualTo("图片已上传，但本地草稿保存失败")
        assertThat(viewModel.uiState.value.isUploading).isFalse()
    }

    @Test
    fun insertHelpers_keepCursorAndIsolateImageUrl() {
        val initial = TextFieldValue("前后", TextRange(1))
        val inserted = insertFloorReference(initial, "alice", 7)
        assertThat(inserted.text).isEqualTo("前@alice #7 后")
        assertThat(inserted.selection.start).isEqualTo("前@alice #7 ".length)
        assertThat(insertFloorReference(inserted, "alice", 7)).isEqualTo(inserted)

        val urlResult = insertImageUrl(TextFieldValue("前后", TextRange(1)), "https://i.v2ex.co/image.png")
        assertThat(urlResult.text).isEqualTo("前\nhttps://i.v2ex.co/image.png\n后")
    }

    private fun viewModel(
        drafts: FakeDraftRepository = FakeDraftRepository(),
        replyRepository: FakeReplyRepository = FakeReplyRepository(),
        imageRepository: ImageUploadRepository? = null,
        authRepository: AuthRepository = FakeAuthRepository(AuthSession(username = "tester")),
        settings: AppSettings = AppSettings(),
        settingsRepository: SettingsRepository = FakeSettingsRepository(settings),
        imageRegistry: DefaultImageHostRegistry = registry("v2ex"),
    ): ReplyEditorViewModel {
        val defaultImageRepository = object : ImageUploadRepository {
            override suspend fun upload(hostId: ImageHostId, contentUri: String) = ImageUploadResult.Failure(
                app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason.Server,
                "unused",
            )
        }
        return ReplyEditorViewModel(
            SavedStateHandle(mapOf("topicId" to 42L)),
            GetReplyConstraintsUseCase(replyRepository),
            CreateReplyUseCase(replyRepository),
            UploadImageUseCase(imageRepository ?: defaultImageRepository),
            LoadReplyDraftUseCase(drafts),
            SaveReplyDraftUseCase(drafts),
            AddReplyDraftImageUseCase(drafts),
            ClearReplyDraftUseCase(drafts),
            ObserveAuthSessionUseCase(authRepository),
            ObserveSettingsUseCase(settingsRepository),
            imageRegistry,
        )
    }

    private fun registry(vararg ids: String, enabled: Boolean = true) =
        DefaultImageHostRegistry(ids.map { id ->
            object : ImageHostAdapter {
                override val descriptor = ImageHostDescriptor(
                    ImageHostId(id), id, ImageHostCapabilities(emptySet(), 0), enabled,
                )
                override suspend fun upload(image: UploadImage) =
                    ImageUploadResult.Failure(
                        ImageUploadFailureReason.Server,
                        "unused",
                    ).let { UploadResult.Failure(UploadFailure(descriptor.id, FailureCategory.Server, RequestStage.Upload, ResultCertainty.Unknown)) }
            }
        })

    private class FakeReplyRepository(
        private val createResult: CreateReplyResult = CreateReplyResult.Success(1),
    ) : ReplyRepository {
        var createCalls = 0
        override suspend fun loadConstraints(topicId: Long) = Result.success(ReplyConstraints(10_000))
        override suspend fun createReply(topicId: Long, content: String): CreateReplyResult {
            createCalls += 1
            return createResult
        }
    }

    private class FakeDraftRepository(
        private val initialDraft: ReplyDraft? = null,
        private val loadGate: CompletableDeferred<Unit>? = null,
        private val throwOnClear: Boolean = false,
        private val throwOnAddImage: Boolean = false,
    ) : ReplyDraftRepository {
        val saved = mutableListOf<ReplyDraft>()
        var clearCalls = 0
        override suspend fun load(topicId: Long): ReplyDraft? {
            loadGate?.await()
            return initialDraft
        }
        override suspend fun save(draft: ReplyDraft) {
            yield()
            saved += draft
        }
        override suspend fun addImage(topicId: Long, image: UploadedReplyImage) {
            if (throwOnAddImage) error("add image failed")
        }
        override suspend fun clear(topicId: Long) {
            if (throwOnClear) error("clear failed")
            clearCalls += 1
        }
    }
}
