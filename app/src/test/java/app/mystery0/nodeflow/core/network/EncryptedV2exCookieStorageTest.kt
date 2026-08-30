package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import app.mystery0.nodeflow.core.security.SecretCipher
import app.mystery0.nodeflow.core.security.StringKeyValueStorage
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Base64

class EncryptedV2exCookieStorageTest {
    @Test
    fun saveAndLoad_roundTripsCompleteCookieListWithoutPlaintext() {
        val storage = FakeStorage()
        val cookieStorage = EncryptedV2exCookieStorage(
            EncryptedKeyValueStore(storage, FakeCipher),
        )
        val cookies = listOf(
            StoredCookie(
                url = "https://www.v2ex.com/",
                value = "A2=auth-secret; Domain=www.v2ex.com; Path=/; Secure",
            ),
            StoredCookie(
                url = "https://www.v2ex.com/notifications",
                value = "V2EX_LANG=zhcn-secret; Domain=www.v2ex.com; Path=/notifications",
            ),
        )

        cookieStorage.save(cookies)

        assertThat(cookieStorage.load()).isEqualTo(cookies)
        assertThat(storage.values["v2ex_cookies"]).doesNotContain("auth-secret")
        assertThat(storage.values["v2ex_cookies"]).doesNotContain("zhcn-secret")
    }

    @Test
    fun fatalRestoreErrorIsPropagated() {
        val storage = FakeStorage()
        storage.values["v2ex_cookies"] = "payload"

        val error = runCatching {
            EncryptedV2exCookieStorage(EncryptedKeyValueStore(storage, FatalCipher)).load()
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AssertionError::class.java)
        assertThat(storage.values).containsEntry("v2ex_cookies", "payload")
    }

    @Test
    fun clear_removesCookies() {
        val storage = FakeStorage()
        val cookieStorage = EncryptedV2exCookieStorage(EncryptedKeyValueStore(storage, FakeCipher))
        cookieStorage.save(listOf(StoredCookie("https://www.v2ex.com/", "A2=auth")))

        cookieStorage.clear()

        assertThat(cookieStorage.load()).isEmpty()
        assertThat(storage.values).doesNotContainKey("v2ex_cookies")
    }

    @Test
    fun corruptedPayload_isRemovedAndReturnsEmptyList() {
        val storage = FakeStorage()
        storage.values["v2ex_cookies"] = "corrupted"
        val cookieStorage = EncryptedV2exCookieStorage(EncryptedKeyValueStore(storage, FakeCipher))

        assertThat(cookieStorage.load()).isEmpty()
        assertThat(storage.values).isEmpty()
    }

    private class FakeStorage : StringKeyValueStorage {
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

    private object FakeCipher : SecretCipher {
        override fun encrypt(plainText: String): String = "encrypted:${Base64.getEncoder().encodeToString(plainText.toByteArray())}"

        override fun decrypt(payload: String): String {
            require(payload.startsWith("encrypted:"))
            return String(Base64.getDecoder().decode(payload.removePrefix("encrypted:")))
        }
    }
}
