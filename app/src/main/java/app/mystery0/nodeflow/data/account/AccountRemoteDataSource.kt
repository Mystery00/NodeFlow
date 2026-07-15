package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.DailyCheckInResult
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import kotlinx.coroutines.CancellationException

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

    suspend fun checkIn(): DailyCheckInResult = safeNetworkCall {
        val dailyResponse = api.dailyMission()
        val dailyUrl = dailyResponse.raw().request.url
        throwForProtectedUrl(dailyUrl)
        requireDailyMissionUrl(dailyUrl)
        val dailyHtml = dailyResponse.bodyStringOrThrow()
        throwIfAuthenticationExpired(dailyHtml)
        if (parser.hasDailyCheckInRiskNotice(dailyHtml)) throw checkInRiskError()
        val currentPage = parser.parseDailyCheckInPage(dailyHtml)
            ?: throw parseError("无法识别签到页面，请稍后重试")
        val current = currentPage.checkIn
        if (current.checkedIn) return@safeNetworkCall DailyCheckInResult(checkIn = current)
        val once = currentPage.redeemOnce
            ?.takeIf { value -> value.all(Char::isDigit) }
            ?: throw parseError("签到凭据无效，请刷新后重试")

        val redeemResponse = api.redeemDailyMission(once = once)
        val finalUrl = redeemResponse.raw().request.url
        throwForProtectedUrl(finalUrl)
        val claimedHtml = redeemResponse.bodyStringOrThrow()
        throwIfAuthenticationExpired(claimedHtml)
        requireDailyMissionUrl(finalUrl)
        if (parser.hasDailyCheckInRiskNotice(claimedHtml)) throw checkInRiskError()
        if (!parser.isDailyCheckInSuccess(claimedHtml)) {
            throw parseError("未能确认签到成功，请刷新后重试")
        }
        val claimed = parser.parseDailyCheckIn(claimedHtml)
            ?: throw parseError("未能读取签到结果，请刷新后重试")
        val rewardBronze = try {
            api.balance().bodyStringOrThrow().let(parser::parseLatestDailyReward)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            null
        }
        DailyCheckInResult(checkIn = claimed, rewardBronze = rewardBronze)
    }

    private fun throwIfAuthenticationExpired(html: String) {
        if (parser.hasSignInEntry(html)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Auth,
                message = "登录状态已失效，请重新登录",
            )
        }
    }

    private fun throwForProtectedUrl(url: okhttp3.HttpUrl) {
        if (!url.isV2exOrigin()) return
        when (url.encodedPath) {
            "/signin" -> throw NodeFlowException(
                kind = NodeFlowException.Kind.Auth,
                message = "登录状态已失效，请重新登录",
            )
            "/restricted" -> throw NodeFlowException(
                kind = NodeFlowException.Kind.AccessDenied,
                message = "当前账号无法执行签到",
            )
        }
    }

    private fun requireDailyMissionUrl(url: okhttp3.HttpUrl) {
        if (!url.isV2exOrigin() || url.encodedPath != "/mission/daily") {
            throw parseError("签到响应地址异常，请稍后重试")
        }
    }

    private fun okhttp3.HttpUrl.isV2exOrigin(): Boolean =
        scheme == "https" && host == "www.v2ex.com"

    private fun parseError(message: String) = NodeFlowException(
        kind = NodeFlowException.Kind.Parse,
        message = message,
    )

    private fun checkInRiskError() = NodeFlowException(
        kind = NodeFlowException.Kind.AccessDenied,
        message = "V2EX 拒绝了本次签到请求，请稍后重试",
    )
}
