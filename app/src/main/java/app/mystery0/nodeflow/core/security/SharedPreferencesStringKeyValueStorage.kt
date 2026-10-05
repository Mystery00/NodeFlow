package app.mystery0.nodeflow.core.security

import android.annotation.SuppressLint
import android.content.SharedPreferences

class SharedPreferencesStringKeyValueStorage(
    private val preferences: SharedPreferences,
) : StringKeyValueStorage {
    override fun read(key: String): String? = preferences.getString(key, null)

    // KTX edit(commit = true) 不返回提交结果；这里必须在持久化失败时抛错。
    @SuppressLint("UseKtx")
    override fun write(key: String, value: String) {
        check(preferences.edit().putString(key, value).commit()) {
            "Unable to persist secure storage value"
        }
    }

    // 删除同样需要检查同步提交结果，不能用忽略失败的 KTX edit 替代。
    @SuppressLint("UseKtx")
    override fun remove(key: String) {
        check(preferences.edit().remove(key).commit()) {
            "Unable to remove secure storage value"
        }
    }
}
