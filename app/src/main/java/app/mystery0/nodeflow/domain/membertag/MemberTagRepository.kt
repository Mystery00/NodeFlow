package app.mystery0.nodeflow.domain.membertag

import kotlinx.coroutines.flow.Flow

interface MemberTagRepository {
    fun observeTags(): Flow<Map<String, List<String>>>
    fun observeSyncedAt(): Flow<Long?>

    /** 拉取并写入缓存；force=false 时距上次同步不足 TTL 直接跳过；未登录跳过。 */
    suspend fun refresh(force: Boolean): Result<Unit>

    /** 写回指定用户的标签（读-改-写记事本），成功后同步更新本地缓存；未登录返回失败。 */
    suspend fun setTagsForUser(
        username: String,
        tags: List<String>,
        avatarUrl: String?,
    ): Result<Unit>
}
