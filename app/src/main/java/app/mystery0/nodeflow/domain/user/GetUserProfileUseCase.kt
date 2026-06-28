package app.mystery0.nodeflow.domain.user

class GetUserProfileUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(username: String, forceRefresh: Boolean = false) =
        repository.user(username, forceRefresh)
}
