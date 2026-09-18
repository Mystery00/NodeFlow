package app.mystery0.nodeflow.domain.topic

import app.mystery0.nodeflow.core.model.ImageShareTarget

/**
 * 图片分享仓库接口，负责将目标图片准备为可供系统分享的 content URI
 */
interface ImageShareRepository {
    /**
     * 准备分享图片
     *
     * @param imageUrl 图片的网络 URL
     * @return 包含 content URI 和 MIME 类型的 [ImageShareTarget]
     */
    suspend fun prepareImageShare(imageUrl: String): Result<ImageShareTarget>
}
