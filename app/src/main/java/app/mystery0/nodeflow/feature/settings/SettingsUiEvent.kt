package app.mystery0.nodeflow.feature.settings

import app.mystery0.nodeflow.core.model.ThemeMode

sealed interface SettingsUiEvent {
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsUiEvent
    data class DynamicColorChanged(val enabled: Boolean) : SettingsUiEvent
    data object ClearCache : SettingsUiEvent
    data object MessageShown : SettingsUiEvent
}
