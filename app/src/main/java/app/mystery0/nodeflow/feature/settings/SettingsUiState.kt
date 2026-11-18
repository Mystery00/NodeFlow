package app.mystery0.nodeflow.feature.settings

import app.mystery0.nodeflow.domain.settings.EchStatus
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.imagehosting.contract.ImageHostDescriptor

data class SettingsUiState(
    val echStatus: EchStatus = EchStatus.Idle,
    val settings: AppSettings = AppSettings(),
    val imageHostDescriptors: List<ImageHostDescriptor> = emptyList(),
    val isClearingCache: Boolean = false,
    val message: String? = null,
    val memberTagSyncedAtEpochSeconds: Long? = null,
    val isSyncingMemberTags: Boolean = false,
)
