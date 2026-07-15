package app.mystery0.nodeflow.domain.account

import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.DailyCheckInResult

interface AccountOverviewRepository {
    suspend fun overview(): Result<AccountOverview>
    suspend fun checkIn(): Result<DailyCheckInResult>
}
