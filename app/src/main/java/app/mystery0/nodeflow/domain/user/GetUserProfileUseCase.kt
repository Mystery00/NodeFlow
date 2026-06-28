package app.mystery0.nodeflow.domain.user

import javax.inject.Inject

class GetUserProfileUseCase @Inject constructor(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(username: String, forceRefresh: Boolean = false) =
        repository.user(username, forceRefresh)
}
