package app.mystery0.nodeflow.domain.topic

import app.mystery0.nodeflow.core.model.TopicDetail

/**
 * 主题详情按需分页器。
 *
 * 每个详情界面持有一个实例，内部累积已加载的回复页；
 * 所有方法返回当前累积状态的完整快照，回复为「从第 1 楼起的连续前缀」。
 */
interface TopicDetailPager {
    /** 加载第 1 页；已有数据且非强刷时直接返回当前快照。 */
    suspend fun loadFirst(forceRefresh: Boolean = false): Result<TopicDetailSnapshot>

    /** 加载下一页；没有更多页时原样返回当前快照。 */
    suspend fun loadNext(): Result<TopicDetailSnapshot>

    /** 顺序补页直到目标楼层已加载或没有更多页；中途失败保留已加载前缀。 */
    suspend fun loadUntilFloor(floor: Int): Result<TopicDetailSnapshot>

    /** 顺序补页到最后一页。 */
    suspend fun loadUntilLastPage(): Result<TopicDetailSnapshot>

    /** 重拉已加载的所有页，全部成功才替换状态；失败时保留原数据。 */
    suspend fun refreshLoaded(): Result<TopicDetailSnapshot>
}

data class TopicDetailSnapshot(
    val detail: TopicDetail,
    val loadedPageCount: Int,
    val totalPageCount: Int,
    val hasMore: Boolean,
)
