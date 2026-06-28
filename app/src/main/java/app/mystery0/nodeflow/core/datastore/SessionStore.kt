package app.mystery0.nodeflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.mystery0.nodeflow.core.model.AuthSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val session: Flow<AuthSession> = context.nodeFlowDataStore.data.map { preferences ->
        AuthSession(
            personalAccessToken = preferences[Keys.personalAccessToken],
            cookieHeader = preferences[Keys.cookieHeader],
            username = preferences[Keys.username],
        )
    }

    suspend fun save(session: AuthSession) {
        context.nodeFlowDataStore.edit { preferences ->
            session.personalAccessToken?.let { preferences[Keys.personalAccessToken] = it }
                ?: preferences.remove(Keys.personalAccessToken)
            session.cookieHeader?.let { preferences[Keys.cookieHeader] = it }
                ?: preferences.remove(Keys.cookieHeader)
            session.username?.let { preferences[Keys.username] = it }
                ?: preferences.remove(Keys.username)
        }
    }

    suspend fun clear() {
        context.nodeFlowDataStore.edit { preferences ->
            preferences.remove(Keys.personalAccessToken)
            preferences.remove(Keys.cookieHeader)
            preferences.remove(Keys.username)
        }
    }

    private object Keys {
        val personalAccessToken = stringPreferencesKey("personal_access_token")
        val cookieHeader = stringPreferencesKey("cookie_header")
        val username = stringPreferencesKey("session_username")
    }
}
