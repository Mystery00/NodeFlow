package app.mystery0.nodeflow.core.security

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.spec.SecretKeySpec

class AndroidKeystoreSecretCipherTest {
    @Test
    fun concurrentResolutionCreatesOnlyOneKey() = runBlocking {
        var key: SecretKeySpec? = null
        val createCount = AtomicInteger()
        val resolver = SynchronizedSecretKeyResolver(
            lock = Any(),
            lookup = { key },
            create = {
                createCount.incrementAndGet()
                Thread.sleep(10)
                SecretKeySpec(ByteArray(32) { 1 }, "AES").also { key = it }
            },
        )

        val results = (1..20).map {
            async(Dispatchers.Default) { resolver.resolve() }
        }.awaitAll()

        assertThat(createCount.get()).isEqualTo(1)
        assertThat(results.distinct()).hasSize(1)
    }
}
