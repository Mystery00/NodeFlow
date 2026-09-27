package app.mystery0.nodeflow.data.reply

data class ImageUploadPayload(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)
