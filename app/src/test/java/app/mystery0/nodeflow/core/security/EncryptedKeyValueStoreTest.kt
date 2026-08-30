package app.mystery0.nodeflow.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EncryptedKeyValueStoreTest {
    @Test
    fun roundTripDoesNotExposePlainTextInBackend() {
        val backend = MemoryBackend()
        val store = EncryptedKeyValueStore(backend, ReversibleCipher)

        store.write("token", "secret-token")

        assertThat(store.read("token")).isEqualTo("secret-token")
        assertThat(backend.values["token"]).isNotEqualTo("secret-token")
    }

    @Test
    fun fatalDecryptErrorIsPropagated() {
        val backend = MemoryBackend()
        val store = EncryptedKeyValueStore(backend, FatalCipher)
        backend.values["key"] = "payload"

        val error = runCatching { store.read("key") }.exceptionOrNull()

        assertThat(error).isInstanceOf(AssertionError::class.java)
        assertThat(backend.values).containsEntry("key", "payload")
    }

    @Test
    fun removeDeletesValue() {
        val backend = MemoryBackend()
        val store = EncryptedKeyValueStore(backend, ReversibleCipher)
        store.write("key", "value")

        store.remove("key")

        assertThat(store.read("key")).isNull()
        assertThat(backend.values).doesNotContainKey("key")
    }

    @Test
    fun corruptPayloadIsRemovedAndReadAsMissing() {
        val backend = MemoryBackend()
        val store = EncryptedKeyValueStore(backend, ReversibleCipher)
        backend.values["key"] = "corrupt"

        assertThat(store.read("key")).isNull()
        assertThat(backend.values).doesNotContainKey("key")
    }

    private class MemoryBackend : StringKeyValueStorage {
        val values = mutableMapOf<String, String>()

        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
        }
        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private object FatalCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(payload: String): String = throw AssertionError("fatal")
    }

    private object ReversibleCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText.reversed()
        override fun decrypt(payload: String): String =
            payload.takeIf { it != "corrupt" }?.reversed()
                ?: error("corrupt payload")
    }
}
