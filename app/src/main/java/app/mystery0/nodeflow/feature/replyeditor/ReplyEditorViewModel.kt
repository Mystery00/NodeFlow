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
) : ViewModel() {
    private val topicId: Long = checkNotNull(savedStateHandle["topicId"])
    private val _uiState = MutableStateFlow(ReplyEditorUiState())
    val uiState: StateFlow<ReplyEditorUiState> = _uiState.asStateFlow()
    private val _effects = Channel<ReplyEditorEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()
    private var currentUsername: String? = null
    private var saveJob: Job? = null
    private var editVersion: Long = 0

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
            ReplyEditorUiEvent.Submit -> submit()
            ReplyEditorUiEvent.Close -> close()
            ReplyEditorUiEvent.FlushDraft -> viewModelScope.launch { flushDraftNow() }
            ReplyEditorUiEvent.ClearRequested -> _uiState.update { it.copy(showClearConfirmation = true) }
            ReplyEditorUiEvent.ClearConfirmed -> clear()
            ReplyEditorUiEvent.ClearCancelled -> _uiState.update { it.copy(showClearConfirmation = false) }
            ReplyEditorUiEvent.MessageConsumed -> _uiState.update { it.copy(message = null) }
            ReplyEditorUiEvent.GalleryRequested -> viewModelScope.launch {
                _effects.send(ReplyEditorEffect.OpenGallery)
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

    private fun upload(contentUri: String) {
        if (currentUsername == null) {
            viewModelScope.launch { _effects.send(ReplyEditorEffect.RequestLogin) }
            return
        }
        val state = _uiState.value
        if (state.isUploading || state.isSubmitting) return
        editVersion += 1
        _uiState.update { it.copy(isUploading = true, message = null, showGalleryAction = false) }
        viewModelScope.launch {
            try {
                flushDraftNow()
                when (val result = uploadImage(contentUri)) {
                    is ImageUploadResult.Success -> {
                        _uiState.update {
                            it.copy(
                                value = insertImageUrl(it.value, result.image.originalUrl),
                                images = it.images + result.image,
                            )
                        }
                        persistUploadedImage(result.image)
                    }
                    is ImageUploadResult.Failure -> {
                        _uiState.update {
                            it.copy(
                                message = result.message,
                                showGalleryAction = result.reason == ImageUploadFailureReason.UploadUnconfirmed,
                            )
                        }
                        if (result.reason == ImageUploadFailureReason.AuthenticationRequired) requestLogin()
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update { it.copy(message = "图片上传失败，请稍后重试") }
            } finally {
                _uiState.update { it.copy(isUploading = false) }
            }
        }
    }

    private suspend fun requestLogin() {
        currentUsername = null
        _uiState.update { it.copy(isLoggedIn = false) }
        _effects.send(ReplyEditorEffect.RequestLogin)
    }

    private suspend fun persistUploadedImage(image: app.mystery0.nodeflow.domain.reply.UploadedReplyImage) {
        try {
            flushDraftNow()
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
        _uiState.value = ReplyEditorUiState(isLoggedIn = true)
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
        saveJob?.cancel()
        editVersion += 1
        viewModelScope.launch { clearDraft(topicId) }
        _uiState.update {
            it.copy(
                value = TextFieldValue(),
                images = emptyList(),
                showClearConfirmation = false,
                message = null,
            )
        }
    }

    private fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 400L
    }
}
