package app.mystery0.nodeflow.feature.settings

import app.mystery0.nodeflow.core.link.ImageHostMatcher

/** 添加自定义图床域名的结果。 */
sealed interface AddImageHostResult {
    data class Added(val hosts: List<String>) : AddImageHostResult
    data object Invalid : AddImageHostResult
    data object Duplicate : AddImageHostResult
}

/** 归一化输入并追加到列表；非法输入、与已有条目或内置图床重复分别返回对应结果。 */
fun addCustomImageHost(current: List<String>, input: String): AddImageHostResult {
    val host = ImageHostMatcher.normalizeHost(input) ?: return AddImageHostResult.Invalid
    if (host in current || host in ImageHostMatcher.BUILT_IN_IMAGE_HOSTS) {
        return AddImageHostResult.Duplicate
    }
    return AddImageHostResult.Added(current + host)
}
