package app.mystery0.nodeflow.domain.settings

import javax.inject.Inject

class ClearCacheUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke() = repository.clearCache()
}
