package app.mystery0.nodeflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.domain.membertag.ObserveMemberTagsUseCase
import app.mystery0.nodeflow.domain.membertag.RefreshMemberTagsUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    observeSettings: ObserveSettingsUseCase,
    observeMemberTags: ObserveMemberTagsUseCase,
    refreshMemberTags: RefreshMemberTagsUseCase,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = observeSettings()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AppSettings(),
        )

    // 开关关闭时对 UI 提供空映射，展示侧不感知开关
    val memberTags: StateFlow<Map<String, List<String>>> =
        combine(observeSettings(), observeMemberTags()) { current, tags ->
            if (current.showMemberTags) tags else emptyMap()
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyMap(),
        )

    init {
        // 启动后台刷新一次，TTL 由仓库控制；浏览路径不再触发网络
        viewModelScope.launch {
            refreshMemberTags()
        }
    }
}
