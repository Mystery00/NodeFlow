package app.mystery0.nodeflow.feature.profile

sealed interface ProfileUiEvent {
    data object Refresh : ProfileUiEvent
    data object Retry : ProfileUiEvent
    data object EditMemberTags : ProfileUiEvent
    data object DismissTagDialog : ProfileUiEvent
    data class SaveMemberTags(val tags: List<String>) : ProfileUiEvent
}
