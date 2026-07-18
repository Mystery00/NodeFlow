package app.mystery0.nodeflow.feature.settings

import app.mystery0.nodeflow.core.model.AppSettings

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val isClearingCache: Boolean = false,
    val message: String? = null,
    val memberTagSyncedAtEpochSeconds: Long? = null,
    val isSyncingMemberTags: Boolean = false,
)
