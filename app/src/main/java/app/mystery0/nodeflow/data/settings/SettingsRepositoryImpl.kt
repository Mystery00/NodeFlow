package app.mystery0.nodeflow.data.settings

import app.mystery0.nodeflow.core.datastore.SettingsStore
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.ThemeMode
import app.mystery0.nodeflow.domain.node.NodeRepository
import app.mystery0.nodeflow.domain.settings.SettingsRepository
import app.mystery0.nodeflow.domain.topic.TopicRepository
import app.mystery0.nodeflow.domain.user.UserRepository
import kotlinx.coroutines.flow.Flow

class SettingsRepositoryImpl(
    private val settingsStore: SettingsStore,
    private val topicRepository: TopicRepository,
    private val nodeRepository: NodeRepository,
    private val userRepository: UserRepository,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = settingsStore.settings

    override suspend fun setThemeMode(themeMode: ThemeMode) {
        settingsStore.setThemeMode(themeMode)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        settingsStore.setDynamicColor(enabled)
    }

    override suspend fun setPinnedHomeNode(node: PinnedHomeNode?) {
        settingsStore.setPinnedHomeNode(node)
    }

    override suspend fun setCustomImageHosts(hosts: List<String>) {
        settingsStore.setCustomImageHosts(hosts)
    }

    override suspend fun setShowMemberTags(enabled: Boolean) {
        settingsStore.setShowMemberTags(enabled)
    }

    override suspend fun clearCache() {
        topicRepository.clearCache()
        nodeRepository.clearCache()
        userRepository.clearCache()
    }
}
