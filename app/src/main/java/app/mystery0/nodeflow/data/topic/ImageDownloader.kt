package app.mystery0.nodeflow.data.topic

import java.io.File

/**
 * 图片下载与缓存获取接口
 */
fun interface ImageDownloader {
    /**
     * 将指定 [imageUrl] 的图片获取并保存到 [targetFile] 中
     *
     * @param imageUrl 图片 URL
     * @param targetFile 目标写入文件
     * @return HTTP 响应的 Content-Type（如果有，如从网络下载），若从本地缓存读取则返回 null
     */
    suspend fun downloadImage(imageUrl: String, targetFile: File): String?
}
