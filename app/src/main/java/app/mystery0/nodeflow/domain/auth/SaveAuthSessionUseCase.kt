package app.mystery0.nodeflow.domain.auth

import app.mystery0.nodeflow.core.model.AuthSession

class SaveAuthSessionUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(session: AuthSession) {
        repository.saveSession(session)
    }
}
