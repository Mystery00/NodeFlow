package app.mystery0.nodeflow.core.notification

import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import app.mystery0.nodeflow.core.security.SecretCipher
import app.mystery0.nodeflow.testing.FakeStringKeyValueStorage
import androidx.work.NetworkType
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test

class NotificationSchedulerTest {
    @Test
    fun scheduleRequiresConnectedNetwork() {
        assertThat(notificationScheduleConstraints().requiredNetworkType)
            .isEqualTo(NetworkType.CONNECTED)
    }

    @Test
    fun clearBaselineRemovesEncryptedReminderValue() = runTest {
        val storage = FakeStringKeyValueStorage()
        val encryptedStorage = EncryptedKeyValueStore(storage, FakeCipher)
        encryptedStorage.write(NotificationReminderChecker.LAST_UNREAD_COUNT_KEY, "3")

        clearNotificationReminderBaseline(encryptedStorage, Dispatchers.Unconfined)

        assertThat(storage.values).doesNotContainKey(NotificationReminderChecker.LAST_UNREAD_COUNT_KEY)
    }

    @Test
    fun clearBaselineRunsOnInjectedDispatcher() = runTest {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "notification-storage-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        val storage = FakeStringKeyValueStorage()
        val encryptedStorage = EncryptedKeyValueStore(storage, FakeCipher)
        encryptedStorage.write(NotificationReminderChecker.LAST_UNREAD_COUNT_KEY, "3")
        try {
            clearNotificationReminderBaseline(encryptedStorage, dispatcher)

            assertThat(storage.lastMutationThread).startsWith("notification-storage-io")
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private object FakeCipher : SecretCipher {
        override fun encrypt(plainText: String): String = "encrypted:$plainText"
        override fun decrypt(payload: String): String = payload.removePrefix("encrypted:")
    }
}
