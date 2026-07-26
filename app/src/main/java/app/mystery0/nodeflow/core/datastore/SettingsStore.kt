package app.mystery0.nodeflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsStore(
    private val context: Context,
) {
    val settings: Flow<AppSettings> = context.nodeFlowDataStore.data.map { preferences ->
        val themeMode = preferences[Keys.themeMode]
            ?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() }
            ?: ThemeMode.System
        AppSettings(
            themeMode = themeMode,
            dynamicColor = preferences[Keys.dynamicColor] ?: true,
            pinnedHomeNode = preferences[Keys.pinnedHomeNodeName]
                ?.takeIf { it.isNotBlank() }
                ?.let { name ->
                    PinnedHomeNode(
                        name = name,
                        title = preferences[Keys.pinnedHomeNodeTitle]
                            ?.takeIf { it.isNotBlank() }
                            ?: name,
                        avatarUrl = preferences[Keys.pinnedHomeNodeAvatarUrl]
                            ?.takeIf { it.isNotBlank() },
                    )
                },
            customImageHosts = decodeCustomImageHosts(preferences[Keys.customImageHosts]),
            showMemberTags = preferences[Keys.showMemberTags] ?: true,
            notificationReminder = preferences[Keys.notificationReminder] ?: false,
        )
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        context.nodeFlowDataStore.edit { preferences ->
            preferences[Keys.themeMode] = themeMode.name
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.nodeFlowDataStore.edit { preferences ->
            preferences[Keys.dynamicColor] = enabled
        }
    }

    suspend fun setPinnedHomeNode(node: PinnedHomeNode?) {
        context.nodeFlowDataStore.edit { preferences ->
            if (node == null) {
                preferences.remove(Keys.pinnedHomeNodeName)
                preferences.remove(Keys.pinnedHomeNodeTitle)
                preferences.remove(Keys.pinnedHomeNodeAvatarUrl)
            } else {
                preferences[Keys.pinnedHomeNodeName] = node.name
                preferences[Keys.pinnedHomeNodeTitle] = node.title
                node.avatarUrl
                    ?.takeIf { it.isNotBlank() }
                    ?.let { preferences[Keys.pinnedHomeNodeAvatarUrl] = it }
                    ?: preferences.remove(Keys.pinnedHomeNodeAvatarUrl)
            }
        }
    }

    suspend fun setShowMemberTags(enabled: Boolean) {
        context.nodeFlowDataStore.edit { preferences ->
            preferences[Keys.showMemberTags] = enabled
        }
    }

    suspend fun setNotificationReminder(enabled: Boolean) {
        context.nodeFlowDataStore.edit { preferences ->
            preferences[Keys.notificationReminder] = enabled
        }
    }

    suspend fun setCustomImageHosts(hosts: List<String>) {
        context.nodeFlowDataStore.edit { preferences ->
            if (hosts.isEmpty()) {
                preferences.remove(Keys.customImageHosts)
            } else {
                preferences[Keys.customImageHosts] = encodeCustomImageHosts(hosts)
            }
        }
    }

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val pinnedHomeNodeName = stringPreferencesKey("pinned_home_node_name")
        val pinnedHomeNodeTitle = stringPreferencesKey("pinned_home_node_title")
        val pinnedHomeNodeAvatarUrl = stringPreferencesKey("pinned_home_node_avatar_url")
        val customImageHosts = stringPreferencesKey("custom_image_hosts")
        val showMemberTags = booleanPreferencesKey("polish_member_tags_enabled")
        val notificationReminder = booleanPreferencesKey("notification_reminder")
    }
}

/** 域名列表 ↔ DataStore 字符串的序列化，换行分隔。 */
internal fun encodeCustomImageHosts(hosts: List<String>): String = hosts.joinToString("\n")

internal fun decodeCustomImageHosts(raw: String?): List<String> =
    raw.orEmpty().split('\n').map(String::trim).filter(String::isNotEmpty)
