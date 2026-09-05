package app.mystery0.nodeflow.feature.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.topic.GetFavoriteTopicsPagingUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@OptIn(ExperimentalCoroutinesApi::class)
class FavoriteTopicsViewModel(
    observeAuthSession: ObserveAuthSessionUseCase,
    getFavoriteTopics: GetFavoriteTopicsPagingUseCase,
) : ViewModel() {
    private val session = observeAuthSession()
        .stateIn(viewModelScope, SharingStarted.Eagerly, AuthSession())
    private val refreshRequests = MutableStateFlow(0)
    private var hasResumed = false

    val uiState = session.map { FavoriteTopicsUiState(it.canLoadFavorites()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FavoriteTopicsUiState())

    val topics = session.flatMapLatest { account ->
        flow {
            // 换号或退出登录时立即丢弃上一账号的页面内存，不写入公共主题缓存。
            emit(PagingData.empty<Topic>())
            if (account.canLoadFavorites()) {
                emitAll(refreshRequests.flatMapLatest { getFavoriteTopics() })
            }
        }
    }.cachedIn(viewModelScope)

    fun onEvent(event: FavoriteTopicsUiEvent) {
        when (event) {
            FavoriteTopicsUiEvent.Refresh -> refreshRequests.update { it + 1 }
            FavoriteTopicsUiEvent.Resume -> {
                // 首次进入由 Paging 加载；从详情或后台返回时重新获取收藏状态。
                if (hasResumed) refreshRequests.update { it + 1 }
                hasResumed = true
            }
        }
    }
}

private fun AuthSession.canLoadFavorites() = !username.isNullOrBlank() && !cookieHeader.isNullOrBlank()
