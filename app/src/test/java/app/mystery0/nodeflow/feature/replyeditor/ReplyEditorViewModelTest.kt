package app.mystery0.nodeflow.feature.replyeditor

import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.domain.auth.AuthRepository
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.reply.AddReplyDraftImageUseCase
import app.mystery0.nodeflow.domain.reply.ClearReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.CreateReplyResult
import app.mystery0.nodeflow.domain.reply.CreateReplyUseCase
import app.mystery0.nodeflow.domain.reply.GetReplyConstraintsUseCase
import app.mystery0.nodeflow.domain.reply.ImageUploadRepository
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import app.mystery0.nodeflow.domain.reply.LoadReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.ReplyConstraints
import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.ReplyDraftRepository
import app.mystery0.nodeflow.domain.reply.ReplyRepository
import app.mystery0.nodeflow.domain.reply.SaveReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.UploadImageUseCase
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage
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
        assertThat(viewModel.uiState.value.isLoggedIn).isFalse()
    }

    @Test
    fun uploadException_releasesEditorLock() = runTest(dispatcher) {
        val imageRepository = object : ImageUploadRepository {
            override suspend fun upload(contentUri: String): ImageUploadResult = error("network")
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
            override suspend fun upload(contentUri: String) = ImageUploadResult.Success(image)
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

    private fun viewModel(
        drafts: FakeDraftRepository,
        replyRepository: FakeReplyRepository = FakeReplyRepository(),
        imageRepository: ImageUploadRepository? = null,
    ): ReplyEditorViewModel {
        val defaultImageRepository = object : ImageUploadRepository {
            override suspend fun upload(contentUri: String) = ImageUploadResult.Failure(
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
            ObserveAuthSessionUseCase(FakeAuthRepository()),
        )
    }

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

    private class FakeAuthRepository : AuthRepository {
        override val session: Flow<AuthSession> = flowOf(AuthSession(username = "tester"))
        override suspend fun loginChallenge(): Result<LoginChallenge> = error("unused")
        override suspend fun login(username: String, password: String, captcha: String, challenge: LoginChallenge): Result<AuthLoginResult> = error("unused")
        override suspend fun verifyTwoFactor(code: String, challenge: TwoFactorChallenge): Result<AuthSession> = error("unused")
        override suspend fun saveSession(session: AuthSession) = Unit
        override suspend fun clearSession() = Unit
    }
}
