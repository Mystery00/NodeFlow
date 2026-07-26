package okhttp3.internal

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.Closeable
import java.io.IOException
import java.lang.reflect.Modifier

class UtilTest {
    @Test
    fun closeQuietly_保留ZoomImage需要的静态二进制签名() {
        val method = Util::class.java.getDeclaredMethod("closeQuietly", Closeable::class.java)

        assertThat(Modifier.isPublic(Util::class.java.modifiers)).isTrue()
        assertThat(Modifier.isStatic(method.modifiers)).isTrue()
        assertThat(Modifier.isPublic(method.modifiers)).isTrue()
        assertThat(method.returnType).isEqualTo(Void.TYPE)
    }

    @Test
    fun closeQuietly_忽略受检异常() {
        val closeable = Closeable { throw IOException("test") }

        Util.closeQuietly(closeable)
    }

    @Test(expected = IllegalStateException::class)
    fun closeQuietly_继续抛出运行时异常() {
        val closeable = Closeable { throw IllegalStateException("test") }

        Util.closeQuietly(closeable)
    }
}
