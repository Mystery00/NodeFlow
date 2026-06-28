package app.mystery0.nodeflow.domain.settings

class ObserveSettingsUseCase(
    private val repository: SettingsRepository,
) {
    operator fun invoke() = repository.settings
}
