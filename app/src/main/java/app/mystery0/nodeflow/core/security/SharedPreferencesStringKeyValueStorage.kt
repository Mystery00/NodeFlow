package app.mystery0.nodeflow.core.security

import android.content.SharedPreferences

class SharedPreferencesStringKeyValueStorage(
    private val preferences: SharedPreferences,
) : StringKeyValueStorage {
    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Unable to persist secure storage value"
        }
    }

    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Unable to remove secure storage value"
        }
    }
}
