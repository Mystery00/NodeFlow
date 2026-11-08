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
    fun providerLimitAllowsCompleteSevenMiBImage() = runTest {
        val bytes = ByteArray(7 * 1024 * 1024) { (it % 251).toByte() }
        val limit = 10L * 1024 * 1024
        val adapter = object : ImageHostAdapter {
            override val descriptor = ImageHostDescriptor(ImageHostId("imgur"), "Imgur",
                ImageHostCapabilities(setOf("image/png"), limit))
            override suspend fun upload(image: UploadImage): UploadResult {
                assertThat(image.bytes).isEqualTo(bytes)
                return UploadResult.Success(UploadedImage(descriptor.id, "image", "https://i.imgur.com/image.png", actualMimeType = "image/png"))
            }
        }
        val reader = ImageContentReader { _, maxBytes ->
            assertThat(maxBytes).isEqualTo(limit)
            ImageContent("test.png", "image/png", bytes.inputStream().readUpTo(maxBytes.toInt()))
        }
        val result = ImageUploadRepositoryImpl(reader, DefaultImageHostRegistry(listOf(adapter)), Dispatchers.Unconfined)
            .upload(ImageHostId("imgur"), "content://test/image")
        assertThat(result).isInstanceOf(ImageUploadResult.Success::class.java)
    }

    @Test
    fun oversizedReaderFailureIsReportedWithoutUploading() = runTest {
        val reader = ImageContentReader { _, _ -> throw ImageReadException.TooLarge() }
        val repository = ImageUploadRepositoryImpl(reader,
            DefaultImageHostRegistry(listOf(v2exAdapter { error("超限图片不应上传") })), Dispatchers.Unconfined)
        val result = repository.upload(ImageHostId("v2ex"), "content://test/image") as ImageUploadResult.Failure
        assertThat(result.reason).isEqualTo(ImageUploadFailureReason.FileTooLarge)
    }
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
            registry = DefaultImageHostRegistry(listOf(v2exAdapter {
                remoteCalled = true
                error("不应上传")
            })),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.upload(ImageHostId("v2ex"), "content://test/large")

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
            registry = DefaultImageHostRegistry(listOf(v2exAdapter { throw IOException("offline") })),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )

        val result = repository.upload(ImageHostId("v2ex"), "content://test/image")

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
            reader = object : ImageContentReader { override fun read(contentUri: String, maxBytes: Long): ImageContent = throw cancellation },
            registry = DefaultImageHostRegistry(listOf(v2exAdapter { error("不应上传") })),
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
        val thrown = try {
            repository.upload(ImageHostId("v2ex"), "content://test/image")
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

    private fun v2exAdapter(upload: suspend (UploadImage) -> UploadResult) = object : ImageHostAdapter {
        override val descriptor = ImageHostDescriptor(ImageHostId("v2ex"), "V2EX",
            ImageHostCapabilities(setOf("image/png", "image/jpeg", "image/gif", "image/webp"), 6L * 1024 * 1024))
        override suspend fun upload(image: UploadImage) = upload.invoke(image)
    }

    private class FakeReader(private val content: ImageContent) : ImageContentReader {
        override fun read(contentUri: String, maxBytes: Long): ImageContent = content
    }
}
