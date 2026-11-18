package app.mystery0.nodeflow.feature.settings

import app.mystery0.nodeflow.core.model.ThemeMode

sealed interface SettingsUiEvent {
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsUiEvent
    data class DynamicColorChanged(val enabled: Boolean) : SettingsUiEvent
    data class ReplyImageHostChanged(val hostId: String) : SettingsUiEvent
    data class AddCustomImageHost(val input: String) : SettingsUiEvent
    data class RemoveCustomImageHost(val host: String) : SettingsUiEvent
    data class MemberTagVisibilityChanged(val enabled: Boolean) : SettingsUiEvent
    data class NotificationReminderChanged(val enabled: Boolean) : SettingsUiEvent
    data class EchChanged(val enabled: Boolean) : SettingsUiEvent
    data class EchDnsChanged(val server: String) : SettingsUiEvent
    data object CheckEch : SettingsUiEvent
    data object SyncMemberTags : SettingsUiEvent
    data object ClearCache : SettingsUiEvent
    data object MessageShown : SettingsUiEvent
}
