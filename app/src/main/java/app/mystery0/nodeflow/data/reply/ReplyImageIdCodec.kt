package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.imagehosting.contract.ImageHostId

data class NamespacedImageId(val hostId: ImageHostId, val remoteId: String) {
    init { require(remoteId.isNotBlank()) { "图片远端 ID 不能为空" } }
}

object ReplyImageIdCodec {
    fun parse(raw: String): NamespacedImageId {
        require(raw.isNotBlank()) { "图片 ID 不能为空" }
        val separator = raw.indexOf(':')
        if (separator < 0) return NamespacedImageId(ImageHostId("v2ex"), raw)
        val host = raw.substring(0, separator)
        val remote = raw.substring(separator + 1)
        require(host.isNotBlank() && remote.isNotBlank()) { "图片命名空间不能为空" }
        return NamespacedImageId(ImageHostId(host), remote)
    }

    fun format(hostId: ImageHostId, remoteId: String): String {
        require(remoteId.isNotBlank()) { "图片远端 ID 不能为空" }
        return "${hostId.value}:$remoteId"
    }

    fun normalize(raw: String): String = format(parse(raw).hostId, parse(raw).remoteId)

    fun deduplicate(rawIds: Iterable<String>): List<String> = rawIds.map(::normalize).distinct()
}
