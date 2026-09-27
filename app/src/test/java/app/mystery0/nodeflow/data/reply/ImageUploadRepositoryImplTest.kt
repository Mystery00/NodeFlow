package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.domain.reply.ImageUploadFailureReason
import app.mystery0.nodeflow.imagehosting.contract.*
import app.mystery0.nodeflow.imagehosting.registry.DefaultImageHostRegistry
import app.mystery0.nodeflow.domain.reply.ImageUploadResult
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException
import kotlinx.coroutines.Dispatchers

class ImageUploadRepositoryImplTest {
    @Test
    fun thirdProvider_isDiscoverableAndRepositoryUploadsWithoutProviderBranch() = runTest {
        val adapter = object : ImageHostAdapter {
            override val descriptor = ImageHostDescriptor(
                ImageHostId("test-host"),
                "Test host",
                ImageHostCapabilities(setOf("image/png"), 100),
            )
            override suspend fun upload(image: UploadImage) = UploadResult.Success(
                UploadedImage(descriptor.id, "remote", "https://test-host.example/image.png", actualMimeType = "image/png"),
            )
        }
        val registry = DefaultImageHostRegistry(listOf(adapter))
        val result = ImageUploadRepositoryImpl(
            FakeReader(ImageContent("test.png", "image/png", byteArrayOf(1))),
            registry,
            Dispatchers.Unconfined,
        ).upload(ImageHostId("test-host"), "content://test/image")

        assertThat(registry.descriptors()).containsExactly(adapter.descriptor)
        assertThat((result as ImageUploadResult.Success).image.imageId).isEqualTo("test-host:remote")
    }

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

    @Test
    fun upload_propagatesReaderCancellation() = runTest {
        val cancellation = CancellationException("cancelled")
        val repository = ImageUploadRepositoryImpl(
            reader = object : ImageContentReader { override fun read(contentUri: String): ImageContent = throw cancellation },
            uploadRemote = { error("不应上传") },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val thrown = try {
            repository.upload("content://test/image")
            error("应传播取消")
        } catch (error: CancellationException) {
            error
        }
        assertThat(thrown).isInstanceOf(CancellationException::class.java)
    }

    @Test
    fun upload_unknownHostDoesNotFallback() = runTest {
        val repository = providerRepository(emptyList())
        val result = repository.upload(ImageHostId("missing"), "content://test/image")
        assertThat(result).isEqualTo(ImageUploadResult.Failure(ImageUploadFailureReason.HostUnavailable, "所选图床不可用，请重新选择"))
    }

    @Test
    fun upload_mapsNamespacedDirectOnlySuccessAndRecoveryAction() = runTest {
        val adapter = fakeAdapter {
            UploadResult.Success(UploadedImage(ImageHostId("imgur"), "same", "https://i.imgur.com/x.png", null, "image/png"))
        }
        val repository = providerRepository(listOf(adapter))
        val success = repository.upload(ImageHostId("imgur"), "content://test/image") as ImageUploadResult.Success
        assertThat(success.image.imageId).isEqualTo("imgur:same")
        assertThat(success.image.detailUrl).isEqualTo(success.image.originalUrl)

        val failing = providerRepository(listOf(fakeAdapter {
            UploadResult.Failure(UploadFailure(ImageHostId("imgur"), FailureCategory.InteractionRequired, RequestStage.Upload, ResultCertainty.Unknown, recoveryAction = RecoveryAction.OpenHostPage("https://imgur.com/upload")))
        })).upload(ImageHostId("imgur"), "content://test/image") as ImageUploadResult.Failure
        assertThat(failing.reason).isEqualTo(ImageUploadFailureReason.UploadUnconfirmed)
        assertThat(failing.recoveryAction).isInstanceOf(RecoveryAction.OpenHostPage::class.java)
    }

    @Test
    fun upload_mapsUnexpectedAdapterExceptionToServer() = runTest {
        val repository = providerRepository(listOf(fakeAdapter { error("broken") }))
        val result = repository.upload(ImageHostId("imgur"), "content://test/image")
        assertThat(result).isEqualTo(ImageUploadResult.Failure(ImageUploadFailureReason.Server, "图床服务暂时不可用，请稍后重试"))
    }

    @Test
    fun upload_mapsServerUnknownAfterSendToUploadUnconfirmed() = runTest {
        val failure = UploadFailure(ImageHostId("imgur"), FailureCategory.Server, RequestStage.Upload, ResultCertainty.Unknown)
        val result = providerRepository(listOf(fakeAdapter { UploadResult.Failure(failure) }))
            .upload(ImageHostId("imgur"), "content://test/image") as ImageUploadResult.Failure
        assertThat(result.reason).isEqualTo(ImageUploadFailureReason.UploadUnconfirmed)
    }

    @Test
    fun upload_preservesRejectedAndNotSubmittedFailureCategories() = runTest {
        val rejected = providerRepository(listOf(fakeAdapter { UploadResult.Failure(UploadFailure(ImageHostId("imgur"), FailureCategory.Server, RequestStage.Upload, ResultCertainty.Rejected)) }))
            .upload(ImageHostId("imgur"), "content://test/image") as ImageUploadResult.Failure
        assertThat(rejected.reason).isEqualTo(ImageUploadFailureReason.Server)
        val notSubmitted = providerRepository(listOf(fakeAdapter { UploadResult.Failure(UploadFailure(ImageHostId("imgur"), FailureCategory.AuthenticationRequired, RequestStage.Preparation, ResultCertainty.NotSubmitted, recoveryAction = RecoveryAction.SignInToHost)) }))
            .upload(ImageHostId("imgur"), "content://test/image") as ImageUploadResult.Failure
        assertThat(notSubmitted.reason).isEqualTo(ImageUploadFailureReason.AuthenticationRequired)
        assertThat(notSubmitted.recoveryAction).isEqualTo(RecoveryAction.SignInToHost)
    }

    @Test
    fun upload_preservesInteractionRecoveryAction() = runTest {
        val action = RecoveryAction.OpenHostPage("https://imgur.com/upload")
        val result = providerRepository(listOf(fakeAdapter { UploadResult.Failure(UploadFailure(ImageHostId("imgur"), FailureCategory.InteractionRequired, RequestStage.Preparation, ResultCertainty.NotSubmitted, recoveryAction = action)) }))
            .upload(ImageHostId("imgur"), "content://test/image") as ImageUploadResult.Failure
        assertThat(result.reason).isEqualTo(ImageUploadFailureReason.UploadUnconfirmed)
        assertThat(result.recoveryAction).isEqualTo(action)
    }

    private fun providerRepository(adapters: List<ImageHostAdapter>) = ImageUploadRepositoryImpl(
        FakeReader(ImageContent("test.png", "image/png", byteArrayOf(1))),
        DefaultImageHostRegistry(adapters),
        Dispatchers.Unconfined,
    )

    private fun fakeAdapter(result: suspend () -> UploadResult) = object : ImageHostAdapter {
        override val descriptor = ImageHostDescriptor(ImageHostId("imgur"), "Imgur", ImageHostCapabilities(setOf("image/png"), 100))
        override suspend fun upload(image: UploadImage) = result()
    }

    private class FakeReader(private val content: ImageContent) : ImageContentReader {
        override fun read(contentUri: String): ImageContent = content
    }
}
