package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser

class AccountRemoteDataSource(
    private val api: V2exRawApi,
    private val parser: V2exHtmlParser,
) {
    suspend fun overview(): AccountOverview = safeNetworkCall {
        val homeHtml = runCatching { api.home().bodyStringOrThrow() }.getOrNull()
        val dailyHtml = runCatching { api.dailyMission().bodyStringOrThrow() }.getOrNull()
        val balanceHtml = runCatching { api.balance().bodyStringOrThrow() }.getOrNull()
        val unreadNotificationCount = homeHtml?.let(parser::parseUnreadNotificationCount)
        val homeIsAnonymous = homeHtml != null &&
            unreadNotificationCount == null &&
            parser.parseLoginAccount(homeHtml) == null
        val protectedPageRequiresLogin = listOfNotNull(dailyHtml, balanceHtml).any(parser::hasSignInEntry)
        if (homeIsAnonymous || protectedPageRequiresLogin) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Auth,
                message = "登录状态已失效，请重新登录",
            )
        }
        AccountOverview(
            unreadNotificationCount = unreadNotificationCount,
            checkIn = dailyHtml?.let(parser::parseDailyCheckIn),
            wealth = balanceHtml?.let(parser::parseAccountWealth),
        )
    }
}
