package app.mystery0.nodeflow.domain.settings

import javax.inject.Inject

class ObserveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke() = repository.settings
}
