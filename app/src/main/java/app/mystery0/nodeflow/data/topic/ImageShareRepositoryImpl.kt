package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.common.ImageTypeDetector
import app.mystery0.nodeflow.core.model.ImageShareTarget
import app.mystery0.nodeflow.domain.topic.ImageShareRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

class ImageShareRepositoryImpl(
    private val imageDownloader: ImageDownloader,
    private val cacheDirProvider: () -> File,
    private val contentUriProvider: (File) -> String,
    private val ioDispatcher: CoroutineDispatcher,
) : ImageShareRepository {

    override suspend fun prepareImageShare(imageUrl: String): Result<ImageShareTarget> = withContext(ioDispatcher) {
        try {
            val sharedImagesDir = File(cacheDirProvider(), "shared_images").apply { mkdirs() }
            cleanOldSharedFiles(sharedImagesDir)

            val tempFile = File.createTempFile("share_temp_", ".tmp", sharedImagesDir)
            try {
                val headerContentType = imageDownloader.downloadImage(imageUrl, tempFile)
                if (!tempFile.exists() || tempFile.length() == 0L) {
                    tempFile.delete()
                    return@withContext Result.failure(IOException("Empty image data"))
                }

                val sampleBytes = tempFile.inputStream().use { input ->
                    val buffer = ByteArray(16)
                    val read = input.read(buffer)
                    if (read > 0) buffer.copyOf(read) else ByteArray(0)
                }

                val format = ImageTypeDetector.detect(
                    bytes = sampleBytes,
                    url = imageUrl,
                    headerContentType = headerContentType,
                )

                val targetFile = File(sharedImagesDir, "share_${System.currentTimeMillis()}.${format.extension}")
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }

                val contentUri = contentUriProvider(targetFile)
                Result.success(
                    ImageShareTarget(
                        contentUri = contentUri,
                        mimeType = format.mimeType,
                    )
                )
            } catch (error: CancellationException) {
                tempFile.delete()
                throw error
            } catch (error: Throwable) {
                tempFile.delete()
                throw error
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun cleanOldSharedFiles(directory: File) {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        directory.listFiles()?.forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                file.delete()
            }
        }
    }
}
