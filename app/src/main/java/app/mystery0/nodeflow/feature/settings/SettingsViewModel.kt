package app.mystery0.nodeflow.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.mystery0.nodeflow.domain.membertag.ObserveMemberTagSyncedAtUseCase
import app.mystery0.nodeflow.domain.membertag.RefreshMemberTagsUseCase
import app.mystery0.nodeflow.domain.settings.ClearCacheUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    observeSettings: ObserveSettingsUseCase,
    observeMemberTagSyncedAt: ObserveMemberTagSyncedAtUseCase,
    private val updateSettings: UpdateSettingsUseCase,
    private val clearCache: ClearCacheUseCase,
    private val refreshMemberTags: RefreshMemberTagsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSettings().collectLatest { settings ->
                _uiState.update { it.copy(settings = settings) }
            }
        }
        viewModelScope.launch {
            observeMemberTagSyncedAt().collectLatest { syncedAt ->
                _uiState.update { it.copy(memberTagSyncedAtEpochSeconds = syncedAt) }
            }
        }
    }

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.ThemeModeChanged -> viewModelScope.launch {
                updateSettings.setThemeMode(event.mode)
            }
            is SettingsUiEvent.DynamicColorChanged -> viewModelScope.launch {
                updateSettings.setDynamicColor(event.enabled)
            }
            is SettingsUiEvent.AddCustomImageHost -> viewModelScope.launch {
                when (val result = addCustomImageHost(
                    current = _uiState.value.settings.customImageHosts,
                    input = event.input,
                )) {
                    is AddImageHostResult.Added ->
                        updateSettings.setCustomImageHosts(result.hosts)
                    AddImageHostResult.Invalid ->
                        _uiState.update { it.copy(message = "无效的图床域名") }
                    AddImageHostResult.Duplicate ->
                        _uiState.update { it.copy(message = "该域名已存在") }
                }
            }
            is SettingsUiEvent.RemoveCustomImageHost -> viewModelScope.launch {
                updateSettings.setCustomImageHosts(
                    _uiState.value.settings.customImageHosts - event.host,
                )
            }
            is SettingsUiEvent.MemberTagVisibilityChanged -> viewModelScope.launch {
                updateSettings.setShowMemberTags(event.enabled)
            }
            SettingsUiEvent.SyncMemberTags -> viewModelScope.launch {
                _uiState.update { it.copy(isSyncingMemberTags = true, message = null) }
                val result = refreshMemberTags(force = true)
                _uiState.update {
                    it.copy(
                        isSyncingMemberTags = false,
                        message = if (result.isSuccess) "用户标签已同步" else "用户标签同步失败",
                    )
                }
            }
            SettingsUiEvent.ClearCache -> viewModelScope.launch {
                _uiState.update { it.copy(isClearingCache = true, message = null) }
                runCatching { clearCache() }
                _uiState.update { it.copy(isClearingCache = false, message = "缓存已清除") }
            }
            SettingsUiEvent.MessageShown -> _uiState.update { it.copy(message = null) }
        }
    }
}
