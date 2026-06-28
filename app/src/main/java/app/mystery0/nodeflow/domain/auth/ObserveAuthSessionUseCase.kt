package app.mystery0.nodeflow.domain.auth

import javax.inject.Inject

class ObserveAuthSessionUseCase @Inject constructor(
    private val repository: AuthRepository,
) {
    operator fun invoke() = repository.session
}
