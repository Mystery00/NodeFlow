package app.mystery0.nodeflow.core.model

/**
 * 图片分享目标信息
 *
 * @property contentUri 系统 FileProvider 生成的 content:// URI 字符串
 * @property mimeType 图片的 MIME 类型，例如 "image/png"、"image/jpeg"、"image/gif" 等
 */
data class ImageShareTarget(
    val contentUri: String,
    val mimeType: String,
)
