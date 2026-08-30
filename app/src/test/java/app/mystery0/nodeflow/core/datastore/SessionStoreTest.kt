package app.mystery0.nodeflow.core.datastore

import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import app.mystery0.nodeflow.core.security.SecretCipher
import app.mystery0.nodeflow.core.security.StringKeyValueStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.Base64
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class SessionStoreTest {
    @Test
    fun save_persistsCompleteSessionForANewInstanceWithoutPlaintext() = runTest {
        val storage = FakeStorage()
        val encryptedStore = EncryptedKeyValueStore(storage, FakeCipher)
        val session = AuthSession(
            personalAccessToken = "pat-secret",
            cookieHeader = "A2=cookie-secret",
            username = "alice",
        )

        SessionStore(encryptedStore).save(session)
        val restored = SessionStore(encryptedStore).session.first()

        assertThat(restored).isEqualTo(session)
        assertThat(storage.values.values.single()).doesNotContain("pat-secret")
        assertThat(storage.values.values.single()).doesNotContain("A2=cookie-secret")
    }

    @Test
    fun clear_removesStoredSessionAndPublishesEmptySessionImmediately() = runTest {
        val storage = FakeStorage()
        val encryptedStore = EncryptedKeyValueStore(storage, FakeCipher)
        val store = SessionStore(encryptedStore)
        store.save(AuthSession(username = "alice"))

        store.clear()

        assertThat(store.session.first()).isEqualTo(AuthSession())
        assertThat(storage.values).isEmpty()
    }

    @Test
    fun saveAndClear_areSerializedAcrossStorageAndFlowPublication() = runTest {
        val storage = BlockingStorage()
        val store = SessionStore(EncryptedKeyValueStore(storage, FakeCipher))

        val saveJob = launch(Dispatchers.Default) {
            store.save(AuthSession(username = "alice"))
        }
        check(storage.writeStarted.await(1, TimeUnit.SECONDS))
        val clearJob = launch(Dispatchers.Default) { store.clear() }

        kotlinx.coroutines.delay(100)
        val clearCompletedWhileWriteBlocked = clearJob.isCompleted

        storage.releaseWrite.countDown()
        saveJob.join()
        clearJob.join()

        assertThat(clearCompletedWhileWriteBlocked).isFalse()
        assertThat(store.session.first()).isEqualTo(AuthSession())
        assertThat(storage.values).isEmpty()
    }

    @Test
    fun saveFailure_doesNotPublishNewSession() = runTest {
        val storage = ToggleStorage()
        val store = SessionStore(EncryptedKeyValueStore(storage, FakeCipher))
        storage.failWrite = true

        val error = runCatching { store.save(AuthSession(username = "alice")) }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(store.session.first()).isEqualTo(AuthSession())
    }

    @Test
    fun fatalRestoreErrorIsPropagated() {
        val storage = FakeStorage()
        storage.values["auth_session"] = "payload"

        val error = runCatching {
            SessionStore(EncryptedKeyValueStore(storage, FatalCipher))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AssertionError::class.java)
    }

    @Test
    fun clearFailure_doesNotPublishLoggedOutSession() = runTest {
        val storage = ToggleStorage()
        val store = SessionStore(EncryptedKeyValueStore(storage, FakeCipher))
        store.save(AuthSession(username = "alice"))
        storage.failRemove = true

        val error = runCatching { store.clear() }.exceptionOrNull()

        assertThat(error).isInstanceOf(IllegalStateException::class.java)
        assertThat(store.session.first()).isEqualTo(AuthSession(username = "alice"))
    }

    @Test
    fun corruptedPayload_isRemovedAndTreatedAsLoggedOut() = runTest {
        val storage = FakeStorage()
        storage.values["auth_session"] = "corrupted"

        val store = SessionStore(EncryptedKeyValueStore(storage, FakeCipher))

        assertThat(store.session.first()).isEqualTo(AuthSession())
        assertThat(storage.values).isEmpty()
    }

    private class BlockingStorage : StringKeyValueStorage {
        val values = ConcurrentHashMap<String, String>()
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)

        override fun read(key: String): String? = values[key]

        override fun write(key: String, value: String) {
            values[key] = value
            writeStarted.countDown()
            check(releaseWrite.await(1, TimeUnit.SECONDS))
        }

        override fun remove(key: String) {
            values.remove(key)
        }
    }

    private class ToggleStorage : StringKeyValueStorage {
        val values = mutableMapOf<String, String>()
        var failWrite = false
        var failRemove = false

        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            check(!failWrite) { "write failed" }
            values[key] = value
        }
        override fun remove(key: String) {
            check(!failRemove) { "remove failed" }
            values.remove(key)
        }
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
