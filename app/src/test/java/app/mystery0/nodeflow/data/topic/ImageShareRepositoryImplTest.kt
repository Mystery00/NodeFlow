package app.mystery0.nodeflow.data.topic

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class ImageShareRepositoryImplTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var cacheDir: File

    @Before
    fun setUp() {
        cacheDir = tempFolder.newFolder("cache")
    }

    @Test
    fun prepareImageShare_downloadsAndCreatesFileWithCorrectMimeType() = runTest(testDispatcher) {
        val pngBytes = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte(), 0x0D.toByte())
        val fakeDownloader = ImageDownloader { _, targetFile ->
            targetFile.writeBytes(pngBytes)
            "image/png"
        }

        val repository = ImageShareRepositoryImpl(
            imageDownloader = fakeDownloader,
            cacheDirProvider = { cacheDir },
            contentUriProvider = { file -> "content://app.mystery0.nodeflow.fileprovider/shared_images/${file.name}" },
            ioDispatcher = testDispatcher,
        )

        val result = repository.prepareImageShare("https://example.com/photo.png")
        assertThat(result.isSuccess).isTrue()
        val target = result.getOrThrow()
        assertThat(target.mimeType).isEqualTo("image/png")
        assertThat(target.contentUri).startsWith("content://app.mystery0.nodeflow.fileprovider/shared_images/share_")
        assertThat(target.contentUri).endsWith(".png")

        val sharedDir = File(cacheDir, "shared_images")
        val files = sharedDir.listFiles().orEmpty()
        assertThat(files).hasLength(1)
        assertThat(files[0].readBytes()).isEqualTo(pngBytes)
    }

    @Test
    fun prepareImageShare_cleansUpOldSharedFiles() = runTest(testDispatcher) {
        val sharedDir = File(cacheDir, "shared_images").apply { mkdirs() }
        val oldFile = File(sharedDir, "share_old.png").apply {
            writeText("old content")
            setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25))
        }
        val recentFile = File(sharedDir, "share_recent.png").apply {
            writeText("recent content")
            setLastModified(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10))
        }

        val jpegBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
        val fakeDownloader = ImageDownloader { _, targetFile ->
            targetFile.writeBytes(jpegBytes)
            null
        }

        val repository = ImageShareRepositoryImpl(
            imageDownloader = fakeDownloader,
            cacheDirProvider = { cacheDir },
            contentUriProvider = { file -> "content://app.test/${file.name}" },
            ioDispatcher = testDispatcher,
        )

        val result = repository.prepareImageShare("https://example.com/pic.jpg")
        assertThat(result.isSuccess).isTrue()

        // 旧文件应该已被清理，新文件依然保留
        assertThat(oldFile.exists()).isFalse()
        assertThat(recentFile.exists()).isTrue()
    }

    @Test
    fun prepareImageShare_handlesDownloadFailureAndCleansTempFile() = runTest(testDispatcher) {
        val fakeDownloader = ImageDownloader { _, _ ->
            throw IOException("Network timeout")
        }

        val repository = ImageShareRepositoryImpl(
            imageDownloader = fakeDownloader,
            cacheDirProvider = { cacheDir },
            contentUriProvider = { file -> "content://app.test/${file.name}" },
            ioDispatcher = testDispatcher,
        )

        val result = repository.prepareImageShare("https://example.com/fail.jpg")
        assertThat(result.isFailure).isTrue()

        val sharedDir = File(cacheDir, "shared_images")
        val tempFiles = sharedDir.listFiles { _, name -> name.startsWith("share_temp_") }.orEmpty()
        assertThat(tempFiles).isEmpty()
    }

    @Test
    fun prepareImageShare_failsWhenDownloadedFileIsEmpty() = runTest(testDispatcher) {
        val fakeDownloader = ImageDownloader { _, targetFile ->
            targetFile.writeBytes(ByteArray(0))
            null
        }

        val repository = ImageShareRepositoryImpl(
            imageDownloader = fakeDownloader,
            cacheDirProvider = { cacheDir },
            contentUriProvider = { file -> "content://app.test/${file.name}" },
            ioDispatcher = testDispatcher,
        )

        val result = repository.prepareImageShare("https://example.com/empty.jpg")
        assertThat(result.isFailure).isTrue()
    }
}
