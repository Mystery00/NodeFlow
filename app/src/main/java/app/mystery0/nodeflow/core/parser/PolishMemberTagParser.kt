package app.mystery0.nodeflow.core.parser

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * 解析与修改 V2EX_Polish 插件同步到记事本的用户标签数据。
 * note 原文 = 字面前缀 V2EX_Polish_settings + JSON，标签在 member-tag 键下。
 * 读取路径任何异常降级为空映射；写路径（patch/buildInitial）任何异常返回 null，
 * 由调用方中止提交，确保不破坏插件的其他设置。
 */
object PolishMemberTagParser {
    const val NOTE_PREFIX = "V2EX_Polish_settings"
    private const val MEMBER_TAG_KEY = "member-tag"

    fun parse(content: String): Map<String, List<String>> {
        val trimmed = content.trim()
        if (!trimmed.startsWith(NOTE_PREFIX)) return emptyMap()
        val json = trimmed.removePrefix(NOTE_PREFIX)
        return runCatching {
            Json.parseToJsonElement(json).jsonObject[MEMBER_TAG_KEY]!!.jsonObject
                .mapValues { (_, entry) ->
                    entry.jsonObject["tags"]?.jsonArray
                        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
                        ?.filter(String::isNotBlank)
                        .orEmpty()
                }
                .filterValues { it.isNotEmpty() }
        }.getOrDefault(emptyMap())
    }

    /**
     * 仅替换 member-tag 下指定用户的标签，其余设置键、条目内未知字段与键顺序原样保留。
     * 用户名大小写不敏感匹配并保留原键名；空标签移除整个条目；失败返回 null。
     */
    fun patch(
        content: String,
        username: String,
        tags: List<String>,
        avatarUrl: String? = null,
    ): String? {
        val trimmed = content.trim()
        if (!trimmed.startsWith(NOTE_PREFIX)) return null
        val root = runCatching {
            Json.parseToJsonElement(trimmed.removePrefix(NOTE_PREFIX)).jsonObject
        }.getOrNull() ?: return null
        val memberTag = when (val element = root[MEMBER_TAG_KEY]) {
            null -> JsonObject(emptyMap())
            is JsonObject -> element
            else -> return null
        }
        val normalized = normalizeTags(tags)
        val entryKey = memberTag.keys.firstOrNull { it.equals(username, ignoreCase = true) }
            ?: username
        val newMemberTag = LinkedHashMap<String, JsonElement>(memberTag)
        if (normalized.isEmpty()) {
            newMemberTag.remove(entryKey)
        } else {
            val existingEntry = memberTag[entryKey] as? JsonObject
            val entry = LinkedHashMap<String, JsonElement>(existingEntry ?: emptyMap())
            entry["tags"] = JsonArray(normalized.map(::JsonPrimitive))
            if (entry["avatar"] == null && !avatarUrl.isNullOrBlank()) {
                entry["avatar"] = JsonPrimitive(avatarUrl)
            }
            newMemberTag[entryKey] = JsonObject(entry)
        }
        val newRoot = LinkedHashMap<String, JsonElement>(root)
        newRoot[MEMBER_TAG_KEY] = JsonObject(newMemberTag)
        return NOTE_PREFIX + JsonObject(newRoot)
    }

    /** 账号从未使用过插件时，构建仅含 member-tag 的最小记事内容；空标签返回 null。 */
    fun buildInitial(username: String, tags: List<String>, avatarUrl: String? = null): String? {
        val normalized = normalizeTags(tags)
        if (normalized.isEmpty()) return null
        val entry = LinkedHashMap<String, JsonElement>()
        entry["tags"] = JsonArray(normalized.map(::JsonPrimitive))
        if (!avatarUrl.isNullOrBlank()) entry["avatar"] = JsonPrimitive(avatarUrl)
        val memberTag = JsonObject(mapOf(username to JsonObject(entry)))
        return NOTE_PREFIX + JsonObject(mapOf(MEMBER_TAG_KEY to memberTag))
    }

    private fun normalizeTags(tags: List<String>): List<String> =
        tags.map(String::trim).filter(String::isNotBlank).distinct()
}
