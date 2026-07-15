package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.DailyCheckInResult
import app.mystery0.nodeflow.domain.account.AccountOverviewRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class AccountOverviewRepositoryImpl(
    private val remoteDataSource: AccountRemoteDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : AccountOverviewRepository {
    override suspend fun overview(): Result<AccountOverview> = withContext(ioDispatcher) {
        runCatching { remoteDataSource.overview() }
    }

    override suspend fun checkIn(): Result<DailyCheckInResult> = withContext(ioDispatcher) {
        runCatching { remoteDataSource.checkIn() }
    }
}
