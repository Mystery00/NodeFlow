package app.mystery0.nodeflow.domain.settings

import app.mystery0.nodeflow.core.model.ThemeMode
import javax.inject.Inject

class UpdateSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend fun setThemeMode(themeMode: ThemeMode) {
        repository.setThemeMode(themeMode)
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        repository.setDynamicColor(enabled)
    }
}
