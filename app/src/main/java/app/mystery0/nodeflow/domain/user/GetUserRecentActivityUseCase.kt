package app.mystery0.nodeflow.domain.user

class GetUserRecentActivityUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(username: String) = repository.recentActivity(username)
}
