package app.mystery0.nodeflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.nodeFlowDataStore.data.map { preferences ->
        val themeMode = preferences[Keys.themeMode]
            ?.let { value -> runCatching { ThemeMode.valueOf(value) }.getOrNull() }
            ?: ThemeMode.System
        AppSettings(
            themeMode = themeMode,
            dynamicColor = preferences[Keys.dynamicColor] ?: true,
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

    private object Keys {
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
    }
}
