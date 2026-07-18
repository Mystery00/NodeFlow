package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

class ImageUploadRepositoryImplTest {
    @Test
    fun upload_rejectsFileLargerThanSixMegabytesWithoutRemoteRequest() = runTest {
        val reader = FakeReader(
            ImageContent("large.png", "image/png", ByteArray(6 * 1024 * 1024 + 1)),
        )
        var remoteCalled = false
        val repository = ImageUploadRepositoryImpl(
            reader = reader,
            uploadRemote = {
                remoteCalled = true
                error("不应上传")
            },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.upload("content://test/large")

        assertThat(result).isEqualTo(
            ImageUploadResult.Failure(
                ImageUploadFailureReason.FileTooLarge,
                "图片不能超过 6 MB",
            ),
        )
        assertThat(remoteCalled).isFalse()
    }

    @Test
    fun upload_mapsRemoteNetworkExceptionToFailure() = runTest {
        val repository = ImageUploadRepositoryImpl(
            reader = FakeReader(ImageContent("test.png", "image/png", byteArrayOf(1))),
            uploadRemote = { throw IOException("offline") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.upload("content://test/image")

        assertThat(result).isEqualTo(
            ImageUploadResult.Failure(
                ImageUploadFailureReason.Network,
                "图片上传失败，请检查网络后重试",
            ),
        )
    }

    private class FakeReader(private val content: ImageContent) : ImageContentReader {
        override fun read(contentUri: String): ImageContent = content
    }
}
