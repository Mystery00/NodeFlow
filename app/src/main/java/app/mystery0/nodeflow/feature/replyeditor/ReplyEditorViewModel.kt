package app.mystery0.nodeflow.feature.replyeditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.reply.AddReplyDraftImageUseCase
import app.mystery0.nodeflow.domain.reply.ClearReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.CreateReplyResult
import app.mystery0.nodeflow.domain.reply.CreateReplyUseCase
import app.mystery0.nodeflow.domain.reply.GetReplyConstraintsUseCase
import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import app.mystery0.nodeflow.domain.reply.LoadReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.ReplyDraft
import app.mystery0.nodeflow.domain.reply.SaveReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.UploadImageUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import app.mystery0.nodeflow.imagehosting.contract.ImageHostId
import app.mystery0.nodeflow.imagehosting.contract.RecoveryAction
import app.mystery0.nodeflow.imagehosting.registry.ImageHostRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ReplyEditorViewModel(
    savedStateHandle: SavedStateHandle,
    private val getReplyConstraints: GetReplyConstraintsUseCase,
    private val createReply: CreateReplyUseCase,
    private val uploadImage: UploadImageUseCase,
    private val loadDraft: LoadReplyDraftUseCase,
    private val saveDraft: SaveReplyDraftUseCase,
    private val addDraftImage: AddReplyDraftImageUseCase,
    private val clearDraft: ClearReplyDraftUseCase,
    observeAuthSession: ObserveAuthSessionUseCase,
    observeSettings: ObserveSettingsUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    imageHostRegistry: ImageHostRegistry,
) : ViewModel() {
    private val topicId: Long = checkNotNull(savedStateHandle["topicId"])
    private val _uiState = MutableStateFlow(
        ReplyEditorUiState(imageHostDescriptors = imageHostRegistry.descriptors()),
    )
    val uiState: StateFlow<ReplyEditorUiState> = _uiState.asStateFlow()
    private val _effects = Channel<ReplyEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
    private var currentUsername: String? = null
    private var saveJob: Job? = null
    private var editVersion: Long = 0
    private var nextUploadRequestId: Long = 0
    private var activeUploadRequest: UploadRequest? = null

    init {
        val draftLoadVersion = editVersion
        viewModelScope.launch {
            loadDraft(topicId)?.takeIf { editVersion == draftLoadVersion }?.let { draft ->
                val start = draft.selectionStart.coerceIn(0, draft.content.length)
                val end = draft.selectionEnd.coerceIn(0, draft.content.length)
                _uiState.update {
                    it.copy(
                        value = TextFieldValue(draft.content, TextRange(start, end)),
                        images = draft.images,
                    )
                }
            }
        }
        viewModelScope.launch {
            observeSettings().collect { settings ->
                if (!_uiState.value.isUploading && !_uiState.value.isSubmitting) {
                    _uiState.update { it.copy(currentImageHostId = settings.replyImageHost) }
                }
            }
        }
        viewModelScope.launch {
            observeAuthSession().collect { session ->
                currentUsername = session.username?.takeIf(String::isNotBlank)
                _uiState.update { it.copy(isLoggedIn = currentUsername != null) }
            }
        }
        refreshConstraints()
    }

    fun onEvent(event: ReplyEditorUiEvent) {
        when (event) {
            ReplyEditorUiEvent.OpenTopicReply -> open()
            is ReplyEditorUiEvent.OpenFloorReply -> {
                if (_uiState.value.isUploading || _uiState.value.isSubmitting) return
                open()
                editVersion += 1
                _uiState.update {
                    it.copy(value = insertFloorReference(it.value, event.username, event.floor))
                }
                scheduleSave()
            }
            is ReplyEditorUiEvent.ContentChanged -> {
                if (_uiState.value.isUploading || _uiState.value.isSubmitting) return
                editVersion += 1
                _uiState.update { it.copy(value = event.value, message = null) }
                scheduleSave()
            }
            is ReplyEditorUiEvent.ImageSelected -> upload(event.contentUri)
            is ReplyEditorUiEvent.ProviderSelected -> selectProvider(event.hostId)
            ReplyEditorUiEvent.Submit -> submit()
            ReplyEditorUiEvent.Close -> close()
            ReplyEditorUiEvent.FlushDraft -> viewModelScope.launch { flushDraftNow() }
            ReplyEditorUiEvent.ClearRequested -> _uiState.update { it.copy(showClearConfirmation = true) }
            ReplyEditorUiEvent.ClearConfirmed -> clear()
            ReplyEditorUiEvent.ClearCancelled -> _uiState.update { it.copy(showClearConfirmation = false) }
            ReplyEditorUiEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
            ReplyEditorUiEvent.GalleryRequested -> viewModelScope.launch {
                val state = _uiState.value
                if (state.recoveryAction is RecoveryAction.OpenHostPage) {
                    _effects.send(
                        ReplyEditorEffect.OpenGallery(
                            hostId = state.currentImageHostId,
                            recoveryAction = state.recoveryAction,
                        ),
                    )
                }
            }
        }
    }

    private fun open() {
        _uiState.update { it.copy(isOpen = true) }
        refreshConstraints()
    }

    private fun close() {
        viewModelScope.launch { flushDraftNow() }
        _uiState.update { it.copy(isOpen = false) }
    }

    private fun refreshConstraints() {
        viewModelScope.launch {
            getReplyConstraints(topicId).onSuccess { constraints ->
                _uiState.update { it.copy(maxLength = constraints.maxLength) }
            }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MILLIS)
            persistDraft()
        }
    }

    private suspend fun flushDraftNow() {
        saveJob?.cancel()
        saveJob = null
        persistDraft()
    }

    private suspend fun persistDraft() {
        val state = _uiState.value
        if (state.value.text.isEmpty() && state.images.isEmpty()) {
            clearDraft(topicId)
            return
        }
        val selection = state.value.selection
        saveDraft(
            ReplyDraft(
                topicId = topicId,
                content = state.value.text,
                selectionStart = selection.start,
                selectionEnd = selection.end,
                images = state.images,
                updatedAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    private fun selectProvider(hostId: String) {
        val state = _uiState.value
        if (state.isUploading || state.isSubmitting) return
        if (state.imageHostDescriptors.none { it.id.value == hostId }) return
        _uiState.update {
            it.copy(
                currentImageHostId = hostId,
                recoveryAction = RecoveryAction.None,
                showGalleryAction = false,
            )
        }
        viewModelScope.launch { updateSettings.setReplyImageHost(hostId) }
    }

    private fun upload(contentUri: String) {
        val state = _uiState.value
        if (state.isUploading || state.isSubmitting) return
        val hostId = state.currentImageHostId
        if (state.imageHostDescriptors.none { it.id.value == hostId }) {
            showMessage("请重新选择图床")
            return
        }
        editVersion += 1
        val request = UploadRequest(
            requestId = ++nextUploadRequestId,
            hostId = hostId,
            topicId = topicId,
            editVersion = editVersion,
        )
        activeUploadRequest = request
        _uiState.update {
            it.copy(
                isUploading = true,
                message = null,
                showGalleryAction = false,
                recoveryAction = RecoveryAction.None,
            )
        }
        viewModelScope.launch {
            try {
                flushDraftNow()
                when (val result = uploadImage(ImageHostId(hostId), contentUri)) {
                    is ImageUploadResult.Success -> {
                        if (!isCurrentUpload(request)) return@launch
                        _uiState.update {
                            it.copy(
                                value = insertImageUrl(it.value, result.image.originalUrl),
                                images = it.images + result.image,
                                recoveryAction = RecoveryAction.None,
                            )
                        }
                        persistUploadedImage(result.image, request)
                    }
                    is ImageUploadResult.Failure -> {
                        if (!isCurrentUpload(request)) return@launch
                        _uiState.update {
                            it.copy(
                                message = result.message,
                                recoveryAction = result.recoveryAction,
                                showGalleryAction = result.recoveryAction is RecoveryAction.OpenHostPage,
                            )
                        }
                        if (result.reason == ImageUploadFailureReason.AuthenticationRequired) requestLogin()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (isCurrentUpload(request)) {
                    _uiState.update { it.copy(message = "图片上传失败，请稍后重试") }
                }
            } finally {
                if (activeUploadRequest == request) {
                    activeUploadRequest = null
                    _uiState.update { it.copy(isUploading = false) }
                }
            }
        }
    }

    private fun isCurrentUpload(request: UploadRequest): Boolean =
        activeUploadRequest == request &&
            request.topicId == topicId &&
            request.hostId == _uiState.value.currentImageHostId &&
            request.editVersion == editVersion

    private suspend fun requestLogin() {
        currentUsername = null
        _uiState.update { it.copy(isLoggedIn = false) }
        _effects.send(ReplyEditorEffect.RequestLogin)
    }

    private suspend fun persistUploadedImage(
        image: app.mystery0.nodeflow.domain.reply.UploadedReplyImage,
        request: UploadRequest,
    ) {
        if (!isCurrentUpload(request)) return
        try {
            flushDraftNow()
            if (!isCurrentUpload(request)) return
            addDraftImage(topicId, image)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            _uiState.update { it.copy(message = "图片已上传，但本地草稿保存失败") }
        }
    }

    private suspend fun clearDraftAfterSuccess() {
        try {
            clearDraft(topicId)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // 服务端成功后，本地草稿清理失败不能改变发布结果。
        }
    }

    private suspend fun handleCreateFailure(result: CreateReplyResult.Failure) {
        _uiState.update { it.copy(isSubmitting = false, message = result.message) }
        if (result.reason == app.mystery0.nodeflow.domain.reply.ReplyFailureReason.AuthenticationRequired) {
            requestLogin()
        }
    }

    private suspend fun handleCreateSuccess(result: CreateReplyResult.Success) {
        _uiState.update {
            it.copy(
                isOpen = false,
                value = TextFieldValue(),
                images = emptyList(),
                isSubmitting = false,
                message = null,
                showGalleryAction = false,
                recoveryAction = RecoveryAction.None,
            )
        }
        _effects.send(ReplyEditorEffect.ReplyCreated(result.floor))
        clearDraftAfterSuccess()
    }

    private suspend fun createReplySafely(content: String) {
        when (val result = createReply(topicId, content)) {
            is CreateReplyResult.Success -> handleCreateSuccess(result)
            is CreateReplyResult.Failure -> handleCreateFailure(result)
        }
    }

    private fun submit() {
        val state = _uiState.value
        val content = state.value.text
        if (content.isBlank()) return showMessage("回复内容不能为空")
        if (content.length > state.maxLength) {
            return showMessage("回复内容不能超过 ${state.maxLength} 个字符")
        }
        val username = currentUsername
        if (username == null) {
            viewModelScope.launch { _effects.send(ReplyEditorEffect.RequestLogin) }
            return
        }
        if (state.isUploading || state.isSubmitting) return
        _uiState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            try {
                flushDraftNow()
                createReplySafely(content)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(isSubmitting = false, message = "回复提交失败，请稍后重试")
                }
            }
        }
    }

    private fun clear() {
        if (_uiState.value.isUploading || _uiState.value.isSubmitting) {
            showMessage("操作进行中，请稍候")
            return
        }
        saveJob?.cancel()
        editVersion += 1
        viewModelScope.launch { clearDraft(topicId) }
        _uiState.update {
            it.copy(
                value = TextFieldValue(),
                images = emptyList(),
                showClearConfirmation = false,
                showGalleryAction = false,
                recoveryAction = RecoveryAction.None,
                message = null,
            )
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private data class UploadRequest(
        val requestId: Long,
        val hostId: String,
        val topicId: Long,
        val editVersion: Long,
    )

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 400L
    }
}
