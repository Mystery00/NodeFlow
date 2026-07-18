package app.mystery0.nodeflow.data.reply

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.InputStream

data class ImageContent(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

fun interface ImageContentReader {
    fun read(contentUri: String): ImageContent
}

class AndroidImageContentReader(
    private val resolver: ContentResolver,
) : ImageContentReader {
    override fun read(contentUri: String): ImageContent {
        val uri = Uri.parse(contentUri)
        require(uri.scheme == ContentResolver.SCHEME_CONTENT)
        val mimeType = resolver.getType(uri)?.lowercase()
            ?: throw ImageReadException.UnsupportedType
        val extension = MIME_EXTENSIONS[mimeType]
            ?: throw ImageReadException.UnsupportedType
        val queriedName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null else cursor.getString(0)
        }
        val fileName = normalizeImageFileName(queriedName, extension)
        val bytes = resolver.openInputStream(uri)?.use { it.readUpTo(MAX_IMAGE_BYTES + 1) }
            ?: throw ImageReadException.Unreadable
        return ImageContent(fileName, mimeType, bytes)
    }

    private companion object {
        val MIME_EXTENSIONS = mapOf(
            "image/png" to "png",
            "image/jpeg" to "jpg",
            "image/gif" to "gif",
            "image/webp" to "webp",
        )
    }
}

internal fun normalizeImageFileName(queriedName: String?, expectedExtension: String): String {
    val allowed = if (expectedExtension == "jpg") setOf("jpg", "jpeg") else setOf(expectedExtension)
    return queriedName
        ?.takeIf { it.substringAfterLast('.', "").lowercase() in allowed }
        ?: "upload.$expectedExtension"
}

sealed class ImageReadException : RuntimeException() {
    data object UnsupportedType : ImageReadException()
    data object Unreadable : ImageReadException()
}

internal const val MAX_IMAGE_BYTES = 6 * 1024 * 1024

internal fun InputStream.readUpTo(limit: Int): ByteArray {
    require(limit >= 0)
    val output = ByteArrayOutputStream(minOf(limit, DEFAULT_BUFFER_SIZE))
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        output.write(buffer, 0, count)
        remaining -= count
    }
    return output.toByteArray()
}
