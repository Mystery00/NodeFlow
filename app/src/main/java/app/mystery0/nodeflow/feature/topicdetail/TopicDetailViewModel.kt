package app.mystery0.nodeflow.feature.topicdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.common.isAccessDenied
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.domain.topic.GetTopicDetailUseCase
import app.mystery0.nodeflow.domain.topic.SetFavoriteUseCase
import app.mystery0.nodeflow.domain.topic.TopicDetailPager
import app.mystery0.nodeflow.domain.topic.TopicDetailSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TopicDetailViewModel(
    savedStateHandle: SavedStateHandle,
    getTopicDetailPager: GetTopicDetailUseCase,
    private val setFavoriteUseCase: SetFavoriteUseCase,
) : ViewModel() {
    private val topicId: Long = checkNotNull(savedStateHandle["topicId"])
    private val initialReplyFloor: Int? =
        savedStateHandle.get<Int>("replyFloor")?.takeIf { it > 0 }
    private val pager: TopicDetailPager = getTopicDetailPager(topicId)
    private val _uiState = MutableStateFlow(TopicDetailUiState())
    val uiState: StateFlow<TopicDetailUiState> = _uiState.asStateFlow()
    private var loadGeneration: Long = 0

    init {
        val generation = ++loadGeneration
        viewModelScope.launch {
            val snapshot = firstLoad(generation, forceRefresh = false) ?: return@launch
            val floor = initialReplyFloor ?: return@launch
            // 深链目标楼层还未加载时顺序补页；补齐后由界面按 initialReplyFloor 定位
            if (snapshot.hasMore && snapshot.detail.replies.size < floor) {
                catchUpToFloor(floor, generation, markTarget = false)
            }
        }
    }

    fun onEvent(event: TopicDetailUiEvent) {
        when (event) {
            TopicDetailUiEvent.Refresh, TopicDetailUiEvent.Retry -> refresh()
            TopicDetailUiEvent.LoadMoreReplies -> loadMore()
            is TopicDetailUiEvent.ReplyCreated -> {
                val generation = ++loadGeneration
                viewModelScope.launch {
                    catchUpToFloor(event.floor, generation, markTarget = true)
                }
            }
            TopicDetailUiEvent.ReplyFloorTargetConsumed ->
                _uiState.update { it.copy(replyFloorTarget = null) }
            TopicDetailUiEvent.ToggleFavorite -> toggleFavorite()
            TopicDetailUiEvent.FavoriteErrorConsumed ->
                _uiState.update { it.copy(favoriteError = null) }
        }
    }

    private fun toggleFavorite() {
        val detail = _uiState.value.detail ?: return
        val currentFavorited = detail.isFavorited ?: return
        val once = detail.favoriteOnce ?: return
        if (_uiState.value.isTogglingFavorite) return
        _uiState.update { it.copy(isTogglingFavorite = true, favoriteError = null) }
        viewModelScope.launch {
            val result = setFavoriteUseCase(topicId, !currentFavorited, once)
            _uiState.update { current ->
                result.fold(
                    onSuccess = { updatedDetail ->
                        // 只更新收藏状态和 once token，保留当前回复列表等内容
                        val merged = current.detail?.copy(
                            isFavorited = updatedDetail?.isFavorited ?: !currentFavorited,
                            favoriteOnce = updatedDetail?.favoriteOnce,
                        )
                        current.copy(
                            detail = merged,
                            isTogglingFavorite = false,
                            favoriteError = null,
                        )
                    },
                    onFailure = { error ->
                        current.copy(
                            isTogglingFavorite = false,
                            favoriteError = error.toUserMessage(),
                        )
                    },
                )
            }
        }
    }

    private fun refresh() {
        val generation = ++loadGeneration
        viewModelScope.launch {
            if (_uiState.value.detail == null) {
                firstLoad(generation, forceRefresh = true)
                return@launch
            }
            _uiState.update { it.copy(isRefreshing = true, errorMessage = null) }
            val result = pager.refreshLoaded()
            if (generation != loadGeneration) return@launch
            _uiState.update { current ->
                result.fold(
                    onSuccess = { snapshot -> applySnapshot(current, snapshot) },
                    onFailure = { error -> applyFailure(current, error) },
                )
            }
        }
    }

    private fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || state.isRefreshing || !state.hasMoreReplies) return
        val generation = loadGeneration
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            val result = pager.loadNext()
            if (generation != loadGeneration) return@launch
            _uiState.update { current ->
                result.fold(
                    onSuccess = { snapshot -> applySnapshot(current, snapshot) },
                    onFailure = { error ->
                        if (error.isAccessDenied()) {
                            applyFailure(current, error)
                        } else {
                            current.copy(
                                isLoadingMore = false,
                                loadMoreError = error.toUserMessage(),
                            )
                        }
                    },
                )
            }
        }
    }

    private suspend fun firstLoad(generation: Long, forceRefresh: Boolean): TopicDetailSnapshot? {
        _uiState.update {
            it.copy(
                isLoading = it.detail == null,
                isRefreshing = forceRefresh && it.detail != null,
                errorMessage = null,
            )
        }
        val result = pager.loadFirst(forceRefresh)
        if (generation != loadGeneration) return null
        var snapshot: TopicDetailSnapshot? = null
        _uiState.update { current ->
            result.fold(
                onSuccess = { loaded ->
                    snapshot = loaded
                    applySnapshot(current, loaded)
                },
                onFailure = { error -> applyFailure(current, error) },
            )
        }
        return snapshot
    }

    /** 顺序补页到目标楼层；[markTarget] 为 true 时完成后触发楼层定位与高亮。 */
    private suspend fun catchUpToFloor(floor: Int, generation: Long, markTarget: Boolean) {
        _uiState.update { it.copy(isLoadingMore = true, loadMoreError = null) }
        val result = pager.loadUntilFloor(floor)
        if (generation != loadGeneration) return
        result.fold(
            onSuccess = { snapshot ->
                _uiState.update { current ->
                    applySnapshot(current, snapshot)
                        .copy(replyFloorTarget = if (markTarget) floor else current.replyFloorTarget)
                }
            },
            onFailure = { error ->
                if (error.isAccessDenied()) {
                    _uiState.update { current -> applyFailure(current, error) }
                    return
                }
                // 补齐中途失败：用已加载前缀同步界面，定位退回已加载末尾
                val partial = pager.loadFirst(forceRefresh = false).getOrNull()
                _uiState.update { current ->
                    val base = partial?.let { applySnapshot(current, it) } ?: current
                    base.copy(
                        isLoadingMore = false,
                        errorMessage = error.toUserMessage(),
                        replyFloorTarget = if (markTarget) floor else base.replyFloorTarget,
                    )
                }
            },
        )
    }

    private fun applySnapshot(
        current: TopicDetailUiState,
        snapshot: TopicDetailSnapshot,
    ): TopicDetailUiState = current.copy(
        isLoading = false,
        isRefreshing = false,
        isLoadingMore = false,
        detail = snapshot.detail,
        hasMoreReplies = snapshot.hasMore,
        loadMoreError = null,
        errorMessage = null,
    )

    private fun applyFailure(
        current: TopicDetailUiState,
        error: Throwable,
    ): TopicDetailUiState = current.copy(
        isLoading = false,
        isRefreshing = false,
        isLoadingMore = false,
        detail = if (error.isAccessDenied()) null else current.detail,
        hasMoreReplies = if (error.isAccessDenied()) false else current.hasMoreReplies,
        errorMessage = error.toUserMessage(),
    )
}
