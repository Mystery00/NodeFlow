package app.mystery0.nodeflow.data.topic

import android.content.Context
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

class DefaultImageDownloader(
    private val context: Context,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder().build(),
    private val ioDispatcher: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
) : ImageDownloader {
    override suspend fun downloadImage(imageUrl: String, targetFile: File): String? = withContext(ioDispatcher) {
        // 1. 优先尝试从 Coil 磁盘缓存中读取原始文件
        val diskCache = SingletonImageLoader.get(context).diskCache
        val snapshot = diskCache?.openSnapshot(imageUrl)
        if (snapshot != null) {
            snapshot.use { snap ->
                val sourceFile = snap.data.toFile()
                sourceFile.copyTo(targetFile, overwrite = true)
            }
            return@withContext null
        }

        // 2. 本地缓存未命中时，通过网络下载
        val request = Request.Builder()
            .url(imageUrl)
            .header("User-Agent", "Mozilla/5.0 (NodeFlow Image Downloader)")
            .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: Failed to download image")
            }
            val body = response.body
            val contentType = response.header("Content-Type")
            body.byteStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            contentType
        }
    }
}
