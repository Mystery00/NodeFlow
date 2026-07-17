package app.mystery0.nodeflow.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** V2EX_Polish 用户标签的本地缓存（username → tags），浏览时只读本缓存。 */
data class CachedMemberTags(
    val tags: Map<String, List<String>>,
    val syncedAtEpochSeconds: Long?,
)

class MemberTagStore(
    private val context: Context,
) {
    fun observe(): Flow<CachedMemberTags> = context.nodeFlowDataStore.data.map { preferences ->
        CachedMemberTags(
            tags = decodeMemberTags(preferences[Keys.tags]),
            syncedAtEpochSeconds = preferences[Keys.syncedAt],
        )
    }

    suspend fun save(tags: Map<String, List<String>>, syncedAtEpochSeconds: Long) {
        context.nodeFlowDataStore.edit { preferences ->
            preferences[Keys.tags] = encodeMemberTags(tags)
            preferences[Keys.syncedAt] = syncedAtEpochSeconds
        }
    }

    suspend fun clear() {
        context.nodeFlowDataStore.edit { preferences ->
            preferences.remove(Keys.tags)
            preferences.remove(Keys.syncedAt)
        }
    }

    private object Keys {
        val tags = stringPreferencesKey("polish_member_tags")
        val syncedAt = longPreferencesKey("polish_member_tags_synced_at")
    }
}

private val MemberTagJson = Json { ignoreUnknownKeys = true }
private val MemberTagMapSerializer =
    MapSerializer(String.serializer(), ListSerializer(String.serializer()))

internal fun encodeMemberTags(tags: Map<String, List<String>>): String =
    MemberTagJson.encodeToString(MemberTagMapSerializer, tags)

internal fun decodeMemberTags(raw: String?): Map<String, List<String>> =
    raw?.takeIf { it.isNotBlank() }
        ?.let { runCatching { MemberTagJson.decodeFromString(MemberTagMapSerializer, it) }.getOrNull() }
        .orEmpty()
