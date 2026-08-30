package app.mystery0.nodeflow.core.security

import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.lang.reflect.Proxy

class SharedPreferencesStringKeyValueStorageTest {
    @Test
    fun writeFailureIsReported() {
        val storage = SharedPreferencesStringKeyValueStorage(failingPreferences())

        val error = runCatching { storage.write("key", "value") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun removeFailureIsReported() {
        val storage = SharedPreferencesStringKeyValueStorage(failingPreferences())

        val error = runCatching { storage.remove("key") }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
    }

    private fun failingPreferences(): SharedPreferences {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "putString", "remove" -> editor
                "commit" -> false
                else -> null
            }
        } as SharedPreferences.Editor
        return Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
    }
}
