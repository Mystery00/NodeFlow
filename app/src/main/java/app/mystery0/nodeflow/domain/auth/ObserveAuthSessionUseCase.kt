package app.mystery0.nodeflow.domain.auth

class ObserveAuthSessionUseCase(
    private val repository: AuthRepository,
) {
    operator fun invoke() = repository.session
}
