package app.mystery0.nodeflow.core.common

data class ImageFormat(
    val mimeType: String,
    val extension: String,
)

/**
 * 图片类型探测器，优先使用文件头魔数（Magic Number），其次使用 Content-Type 和 URL 扩展名进行推断。
 */
object ImageTypeDetector {
    private val JPEG_HEADER = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
    private val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50.toByte(), 0x4E.toByte(), 0x47.toByte())
    private val GIF_87A = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '7'.code.toByte(), 'a'.code.toByte())
    private val GIF_89A = byteArrayOf('G'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), '8'.code.toByte(), '9'.code.toByte(), 'a'.code.toByte())
    private val RIFF = byteArrayOf('R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte())
    private val WEBP = byteArrayOf('W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte())
    private val BMP = byteArrayOf(0x42.toByte(), 0x4D.toByte())

    fun detect(
        bytes: ByteArray,
        url: String? = null,
        headerContentType: String? = null,
    ): ImageFormat {
        // 1. 优先通过头部魔数判断真实文件类型
        if (bytes.size >= 3 && bytes[0] == JPEG_HEADER[0] && bytes[1] == JPEG_HEADER[1] && bytes[2] == JPEG_HEADER[2]) {
            return ImageFormat("image/jpeg", "jpg")
        }
        if (bytes.size >= 4 && bytes.take(4) == PNG_HEADER.toList()) {
            return ImageFormat("image/png", "png")
        }
        if (bytes.size >= 6 && (bytes.take(6) == GIF_87A.toList() || bytes.take(6) == GIF_89A.toList())) {
            return ImageFormat("image/gif", "gif")
        }
        if (bytes.size >= 12 && bytes.take(4) == RIFF.toList() && bytes.slice(8..11) == WEBP.toList()) {
            return ImageFormat("image/webp", "webp")
        }
        if (bytes.size >= 2 && bytes[0] == BMP[0] && bytes[1] == BMP[1]) {
            return ImageFormat("image/bmp", "bmp")
        }

        // 2. 其次通过 HTTP 响应头的 Content-Type 推断
        if (!headerContentType.isNullOrBlank()) {
            val clean = headerContentType.substringBefore(';').trim().lowercase()
            when (clean) {
                "image/jpeg", "image/jpg" -> return ImageFormat("image/jpeg", "jpg")
                "image/png" -> return ImageFormat("image/png", "png")
                "image/gif" -> return ImageFormat("image/gif", "gif")
                "image/webp" -> return ImageFormat("image/webp", "webp")
                "image/bmp" -> return ImageFormat("image/bmp", "bmp")
                "image/svg+xml" -> return ImageFormat("image/svg+xml", "svg")
            }
        }

        // 3. 再次通过 URL 扩展名推断
        if (!url.isNullOrBlank()) {
            val path = url.substringBefore('?').substringBefore('#')
            val ext = path.substringAfterLast('.', "").lowercase()
            when (ext) {
                "jpg", "jpeg" -> return ImageFormat("image/jpeg", "jpg")
                "png" -> return ImageFormat("image/png", "png")
                "gif" -> return ImageFormat("image/gif", "gif")
                "webp" -> return ImageFormat("image/webp", "webp")
                "bmp" -> return ImageFormat("image/bmp", "bmp")
                "svg" -> return ImageFormat("image/svg+xml", "svg")
            }
        }

        // 4. 默认兜底为 JPEG
        return ImageFormat("image/jpeg", "jpg")
    }
}
