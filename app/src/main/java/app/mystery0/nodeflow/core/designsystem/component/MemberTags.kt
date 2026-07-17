package app.mystery0.nodeflow.core.designsystem.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * V2EX_Polish 用户标签（username → tags），由应用根部从本地缓存注入；
 * 开关关闭时注入空映射，展示侧无需感知开关。
 */
val LocalMemberTags: ProvidableCompositionLocal<Map<String, List<String>>> =
    compositionLocalOf { emptyMap() }

/** 按用户名查标签，V2EX 用户名不区分大小写。 */
fun memberTagsFor(tags: Map<String, List<String>>, username: String): List<String> {
    if (username.isBlank() || tags.isEmpty()) return emptyList()
    tags[username]?.let { return it }
    val lower = username.lowercase()
    return tags.entries.firstOrNull { it.key.lowercase() == lower }?.value.orEmpty()
}
