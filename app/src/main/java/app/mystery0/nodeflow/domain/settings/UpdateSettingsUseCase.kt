package app.mystery0.nodeflow.domain.settings

import app.mystery0.nodeflow.core.model.PinnedHomeNode
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

    suspend fun setPinnedHomeNode(node: PinnedHomeNode?) {
        repository.setPinnedHomeNode(node)
    }

    suspend fun setCustomImageHosts(hosts: List<String>) {
        repository.setCustomImageHosts(hosts)
    }

    suspend fun setShowMemberTags(enabled: Boolean) {
        repository.setShowMemberTags(enabled)
    }

    suspend fun setNotificationReminder(enabled: Boolean) {
        repository.setNotificationReminder(enabled)
    }
}
