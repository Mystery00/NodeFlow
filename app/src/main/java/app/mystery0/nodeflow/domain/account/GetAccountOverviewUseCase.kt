package app.mystery0.nodeflow.domain.account

class GetAccountOverviewUseCase(
    private val repository: AccountOverviewRepository,
) {
    suspend operator fun invoke() = repository.overview()
}
