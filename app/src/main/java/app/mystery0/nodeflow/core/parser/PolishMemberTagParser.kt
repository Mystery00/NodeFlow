package app.mystery0.nodeflow.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析 V2EX_Polish 插件同步到记事本的用户标签数据。
 * note 原文 = 字面前缀 V2EX_Polish_settings + JSON，标签在 member-tag 键下。
 * 数据由插件写入，任何异常都降级为空映射。
 */
object PolishMemberTagParser {
    const val NOTE_PREFIX = "V2EX_Polish_settings"

    fun parse(content: String): Map<String, List<String>> {
        val trimmed = content.trim()
        if (!trimmed.startsWith(NOTE_PREFIX)) return emptyMap()
        val json = trimmed.removePrefix(NOTE_PREFIX)
        return runCatching {
            Json.parseToJsonElement(json).jsonObject["member-tag"]!!.jsonObject
                .mapValues { (_, entry) ->
                    entry.jsonObject["tags"]?.jsonArray
                        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        ?.filter(String::isNotBlank)
                        .orEmpty()
                }
                .filterValues { it.isNotEmpty() }
        }.getOrDefault(emptyMap())
    }
}
