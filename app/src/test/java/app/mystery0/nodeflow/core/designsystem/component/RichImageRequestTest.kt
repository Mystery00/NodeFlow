package app.mystery0.nodeflow.core.designsystem.component

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.mystery0.nodeflow.core.model.RichImage
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RichImageRequestTest {
    @get:Rule val rule = createComposeRule()

    @OptIn(DelicateCoilApi::class)
    @Test fun retriesPreserveOriginalRequestUrl() {
        val urls = CopyOnWriteArrayList<Any>()
        val loader = ImageLoader.Builder(RuntimeEnvironment.getApplication()).components {
            add(Interceptor { chain ->
                urls += chain.request.data
                ErrorResult(image = null, request = chain.request, throwable = java.io.IOException("offline"))
            })
        }.build()
        SingletonImageLoader.setUnsafe(loader)
        try {
            val url = "https://example.test/image.png#original"
            rule.setContent {
                MaterialTheme { RichBlockImage(RichImage(url), rememberRichContentImageSizeCache(Unit), {}, {}) }
            }
            rule.waitUntil(5_000) { urls.isNotEmpty() }
            assertThat(urls.first()).isEqualTo(url)
            rule.onNodeWithText("图片加载失败，点击重试").performClick()
            rule.waitUntil(5_000) { urls.size >= 2 }
            assertThat(urls.toList()).containsExactly(url, url).inOrder()
        } finally {
            SingletonImageLoader.reset()
            loader.shutdown()
        }
    }
}
