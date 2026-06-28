package app.mystery0.nodeflow.feature.editor

sealed interface EditorUiEvent {
    data class TitleChanged(val value: String) : EditorUiEvent
    data class ContentChanged(val value: String) : EditorUiEvent
}
