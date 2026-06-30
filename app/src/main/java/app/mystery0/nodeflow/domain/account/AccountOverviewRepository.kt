package app.mystery0.nodeflow.domain.account

import app.mystery0.nodeflow.core.model.AccountOverview

interface AccountOverviewRepository {
    suspend fun overview(): Result<AccountOverview>
}
