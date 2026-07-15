package app.mystery0.nodeflow.domain.account

class CheckInUseCase(
    private val repository: AccountOverviewRepository,
) {
    suspend operator fun invoke() = repository.checkIn()
}
