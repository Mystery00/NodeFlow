package app.mystery0.nodeflow.core.notification

import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import app.mystery0.nodeflow.core.security.SecretCipher
import app.mystery0.nodeflow.core.security.StringKeyValueStorage
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationReminderCheckerTest {
    @Test
    fun firstPositiveCountNotifies() = runTest {
        val storage = MemoryStorage()
        val checker = NotificationReminderChecker(
            unreadCountProvider = NotificationUnreadCountProvider { 3 },
            store = EncryptedKeyValueStore(storage, IdentityCipher),
        )

        assertThat(checker.check().shouldNotify).isTrue()
        assertThat(checker.check().shouldNotify).isFalse()
    }

    @Test
    fun disabledAfterFetch_doesNotWriteBaselineOrNotify() = runTest {
        val storage = MemoryStorage()
        val enabled = MutableStateFlow(true)
        val checker = NotificationReminderChecker(
            unreadCountProvider = NotificationUnreadCountProvider {
                enabled.value = false
                3
            },
            store = EncryptedKeyValueStore(storage, IdentityCipher),
            reminderEnabled = enabled,
        )

        val decision = checker.check()

        assertThat(decision.shouldNotify).isFalse()
        assertThat(storage.values).doesNotContainKey(NotificationReminderChecker.LAST_UNREAD_COUNT_KEY)
    }

    @Test
    fun disabledDuringWrite_removesBaselineAndSuppressesNotification() = runTest {
        val storage = MemoryStorage()
        val enabled = MutableStateFlow(true)
        storage.onWrite = { enabled.value = false }
        val checker = NotificationReminderChecker(
            unreadCountProvider = NotificationUnreadCountProvider { 3 },
            store = EncryptedKeyValueStore(storage, IdentityCipher),
            reminderEnabled = enabled,
        )

        val decision = checker.check()

        assertThat(decision.shouldNotify).isFalse()
        assertThat(storage.values).doesNotContainKey(NotificationReminderChecker.LAST_UNREAD_COUNT_KEY)
    }

    @Test
    fun countDecisionTableIsApplied() = runTest {
        val storage = MemoryStorage()
        val store = EncryptedKeyValueStore(storage, IdentityCipher)
        val counts = ArrayDeque(listOf(3, 5, 2, 0, 2))
        val checker = NotificationReminderChecker(
            NotificationUnreadCountProvider { counts.removeFirst() },
            store,
        )

        assertThat(checker.check()).isEqualTo(NotificationReminderDecision(3, true))
        assertThat(checker.check()).isEqualTo(NotificationReminderDecision(5, true))
        assertThat(checker.check()).isEqualTo(NotificationReminderDecision(2, false))
        assertThat(checker.check()).isEqualTo(NotificationReminderDecision(0, false))
        assertThat(checker.check()).isEqualTo(NotificationReminderDecision(2, true))
    }

    private class MemoryStorage : StringKeyValueStorage {
        val values = mutableMapOf<String, String>()
        var onWrite: (() -> Unit)? = null
        override fun read(key: String): String? = values[key]
        override fun write(key: String, value: String) {
            values[key] = value
            onWrite?.invoke()
        }
        override fun remove(key: String) { values.remove(key) }
    }

    private object IdentityCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(payload: String): String = payload
    }
}
