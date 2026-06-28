package app.mystery0.nodeflow.domain.settings

class ClearCacheUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke() = repository.clearCache()
}
