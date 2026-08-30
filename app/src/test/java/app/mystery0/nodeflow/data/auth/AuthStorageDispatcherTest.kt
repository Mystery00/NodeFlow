package app.mystery0.nodeflow.data.auth

import com.google.common.truth.Truth.assertThat
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Test

class AuthStorageDispatcherTest {
    @Test
    fun storageMutationRunsOnInjectedDispatcher() {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "auth-storage-io")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        try {
            val threadName = runBlocking {
                runAuthStorageMutation(dispatcher) { Thread.currentThread().name }
            }

            assertThat(threadName).startsWith("auth-storage-io")
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }
}
