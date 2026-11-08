package app.mystery0.nodeflow.feature.replyeditor

import androidx.compose.ui.text.input.TextFieldValue
import app.mystery0.nodeflow.domain.reply.UploadedReplyImage
import app.mystery0.nodeflow.imagehosting.contract.RecoveryAction

data class ReplyEditorUiState(
    val isOpen: Boolean = false,
    val value: TextFieldValue = TextFieldValue(),
    val images: List<UploadedReplyImage> = emptyList(),
    val currentImageHostId: String = "v2ex",
    val recoveryAction: RecoveryAction = RecoveryAction.None,
    val isUploading: Boolean = false,
    val isSubmitting: Boolean = false,
    val maxLength: Int = 10_000,
    val message: String? = null,
    val showGalleryAction: Boolean = false,
    val showClearConfirmation: Boolean = false,
)

sealed interface ReplyEditorUiEvent {
    data object OpenTopicReply : ReplyEditorUiEvent
    data class OpenFloorReply(val username: String, val floor: Int) : ReplyEditorUiEvent
    data class ContentChanged(val value: TextFieldValue) : ReplyEditorUiEvent
    data class ImageSelected(val contentUri: String) : ReplyEditorUiEvent
    data object Submit : ReplyEditorUiEvent
    data object Close : ReplyEditorUiEvent
    data object FlushDraft : ReplyEditorUiEvent
    data object ClearRequested : ReplyEditorUiEvent
    data object ClearConfirmed : ReplyEditorUiEvent
    data object ClearCancelled : ReplyEditorUiEvent
    data object GalleryRequested : ReplyEditorUiEvent
}

sealed interface ReplyEditorEffect {
    data object RequestLogin : ReplyEditorEffect
    data class OpenGallery(
        val recoveryAction: RecoveryAction,
    ) : ReplyEditorEffect
    data class ReplyCreated(val floor: Int) : ReplyEditorEffect
}
