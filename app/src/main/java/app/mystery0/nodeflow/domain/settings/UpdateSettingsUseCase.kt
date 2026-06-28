package app.mystery0.nodeflow.domain.settings

import app.mystery0.nodeflow.core.model.ThemeMode

class UpdateSettingsUseCase(
    private val repository: SettingsRepository,
) {
    suspend fun setThemeMode(themeMode: ThemeMode) {
        repository.setThemeMode(themeMode)
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        repository.setDynamicColor(enabled)
    }
}
