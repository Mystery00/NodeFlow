package app.mystery0.nodeflow

import android.app.Application
import app.mystery0.nodeflow.data.topic.DefaultImageDownloader
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.disk.DiskCache
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import okio.Path.Companion.toOkioPath
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** 网络与缓存使用本地替身；Android ImageDecoder 的 PNG/GIF 解码由设备测试覆盖。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CoilImageLoaderTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun failedDownloadClosesResponseBody() = runBlocking {
        var closed = false
        val body = object : okhttp3.ResponseBody() {
            override fun contentType(): okhttp3.MediaType? = null
            override fun contentLength() = 5L
            override fun source() = okio.Buffer().writeUtf8("error")
            override fun close() { closed = true }
        }
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            okhttp3.Response.Builder().request(chain.request()).protocol(okhttp3.Protocol.HTTP_1_1)
                .code(500).message("Server error").body(body).build()
        }.build()
        val result = runCatching {
            DefaultImageDownloader(RuntimeEnvironment.getApplication(), client)
                .downloadImage("https://example.test/failed.png", temporaryFolder.newFile())
        }
        assertThat(result.exceptionOrNull()).isInstanceOf(java.io.IOException::class.java)
        assertThat(closed).isTrue()
    }

    @OptIn(DelicateCoilApi::class)
    @Test fun networkSvgCanBeDecodedAndSavedFromDiskCache() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val bytes = """<svg xmlns="http://www.w3.org/2000/svg" width="8" height="6"><rect width="8" height="6" fill="red"/></svg>""".toByteArray()
        val loader = NodeFlowApplication().newImageLoader(context).newBuilder()
            .diskCache(DiskCache.Builder().directory(temporaryFolder.newFolder("cache").toOkioPath()).build())
            .build()
        val server = MockWebServer()
        server.start()
        try {
            SingletonImageLoader.setUnsafe(loader)
            server.enqueue(MockResponse().setHeader("Content-Type", "image/svg+xml").setBody(Buffer().write(bytes)))
            val url = server.url("/image.svg").toString()
            val result = loader.execute(ImageRequest.Builder(context).data(url).build())
            assertThat(result).isInstanceOf(SuccessResult::class.java)
            val image = (result as SuccessResult).image
            assertThat(image.width).isEqualTo(8)
            assertThat(image.height).isEqualTo(6)
            assertThat(loader.diskCache!!.openSnapshot(url)?.use { it.data.toFile().readBytes() }).isEqualTo(bytes)

            // 关闭服务后仍能保存原始文件，证明下载器使用了同一个 Coil 磁盘缓存。
            server.shutdown()
            val target = temporaryFolder.newFile("saved.svg")
            DefaultImageDownloader(context).downloadImage(url, target)
            assertThat(target.readBytes()).isEqualTo(bytes)
            assertThat(server.requestCount).isEqualTo(1)
        } finally {
            SingletonImageLoader.reset()
            loader.shutdown()
            server.close()
        }
    }
}
