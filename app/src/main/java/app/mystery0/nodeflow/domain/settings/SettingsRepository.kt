package app.mystery0.nodeflow.domain.settings

import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setPinnedHomeNode(node: PinnedHomeNode?)
    suspend fun setCustomImageHosts(hosts: List<String>)
    suspend fun setShowMemberTags(enabled: Boolean)
    suspend fun clearCache()
}
