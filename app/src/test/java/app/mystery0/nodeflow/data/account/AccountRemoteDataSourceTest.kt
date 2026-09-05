package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class AccountRemoteDataSourceTest {
    private val parser = V2exHtmlParser()

    @Test
    fun overview_combinesLoginStatePages() = runTest {
        val api = FakeV2exRawApi(
            homeHtml = """
                <a href="/signout?once=12345">Sign Out</a>
                <input type="button" class="super special button" value="5 条未读提醒" />
            """.trimIndent(),
            dailyHtml = """
                <div class="cell">
                  <span>currentUser 已连续签到 9 天</span>
                  <input type="button" onclick="location.href = '/balance';" value="已签到" />
                </div>
            """.trimIndent(),
            balanceHtml = """
                <table>
                  <tr><td>金币</td><td>2</td></tr>
                  <tr><td>银币</td><td>34</td></tr>
                  <tr><td>铜币</td><td>5,678</td></tr>
                </table>
            """.trimIndent(),
        )
        val dataSource = AccountRemoteDataSource(api, parser)

        val overview = dataSource.overview()

        assertThat(overview.unreadNotificationCount).isEqualTo(5)
        assertThat(overview.checkIn?.checkedIn).isTrue()
        assertThat(overview.checkIn?.continuousDays).isEqualTo(9)
        assertThat(overview.wealth?.gold).isEqualTo(2)
        assertThat(overview.wealth?.silver).isEqualTo(34)
        assertThat(overview.wealth?.bronze).isEqualTo(5678)
    }

    @Test
    fun overview_throwsAuthErrorWhenHomePageIsAnonymous() = runTest {
        val api = FakeV2exRawApi(
            homeHtml = """<a href="/signin">Sign In</a>""",
            dailyHtml = """<a href="/signin">Sign In</a>""",
            balanceHtml = """<a href="/signin">Sign In</a>""",
        )
        val dataSource = AccountRemoteDataSource(api, parser)

        val result = runCatching { dataSource.overview() }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Auth)
    }

    @Test
    fun overview_throwsAuthErrorWhenProtectedPagesRedirectToSignIn() = runTest {
        val api = FakeV2exRawApi(
            homeHtml = """
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <a href="/signout?once=12345">Sign Out</a>
                </div>
            """.trimIndent(),
            dailyHtml = """<a href="/signin">Sign In</a>""",
            balanceHtml = """<form action="/signin"><input name="once" /></form>""",
        )
        val dataSource = AccountRemoteDataSource(api, parser)

        val result = runCatching { dataSource.overview() }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Auth)
    }

    @Test
    fun overview_keepsPartialDataWhenSomePagesCannotBeParsed() = runTest {
        val api = FakeV2exRawApi(
            homeHtml = """
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <a href="/signout?once=12345">Sign Out</a>
                </div>
            """.trimIndent(),
            dailyHtml = """<div>Unknown daily page markup</div>""",
            balanceHtml = """<div>Unknown balance page markup</div>""",
        )
        val dataSource = AccountRemoteDataSource(api, parser)

        val overview = dataSource.overview()

        assertThat(overview.unreadNotificationCount).isEqualTo(0)
        assertThat(overview.checkIn).isNull()
        assertThat(overview.wealth).isNull()
    }

    @Test
    fun checkIn_redeemsFreshOnceAndReadsLatestReward() = runTest {
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml("84830"),
            redeemHtml = claimedDailyHtml(),
            redeemFinalUrl = "https://www.v2ex.com/mission/daily",
            balanceHtml = """
                <table><tr><td>今天</td><td>每日登录奖励</td><td>+12</td><td>0</td><td>记录</td></tr></table>
            """.trimIndent(),
        )
        val dataSource = AccountRemoteDataSource(api, parser)

        val result = dataSource.checkIn()

        assertThat(api.redeemCalls).isEqualTo(1)
        assertThat(api.lastRedeemOnce).isEqualTo("84830")
        assertThat(api.lastRedeemReferer).isEqualTo("https://www.v2ex.com/mission/daily")
        assertThat(result.rewardBronze).isEqualTo(12)
        assertThat(result.checkIn.checkedIn).isTrue()
    }

    @Test
    fun checkIn_returnsCurrentStateWithoutRedeemingWhenAlreadyCheckedIn() = runTest {
        val api = FakeV2exRawApi(dailyHtml = claimedDailyHtml())

        val result = AccountRemoteDataSource(api, parser).checkIn()

        assertThat(api.redeemCalls).isEqualTo(0)
        assertThat(result.checkIn.checkedIn).isTrue()
        assertThat(result.rewardBronze).isNull()
    }

    @Test
    fun checkIn_doesNotRedeemWhenOnceIsMissing() = runTest {
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml(once = "").replace("once=", "missing="),
        )

        val result = runCatching { AccountRemoteDataSource(api, parser).checkIn() }

        assertThat(api.redeemCalls).isEqualTo(0)
        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun checkIn_rejectsUnexpectedRedirectTarget() = runTest {
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml("84830"),
            redeemHtml = claimedDailyHtml(),
            redeemFinalUrl = "https://www.v2ex.com/",
        )

        val result = runCatching { AccountRemoteDataSource(api, parser).checkIn() }

        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun checkIn_rejectsUnexpectedRedirectHost() = runTest {
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml("84830"),
            redeemHtml = claimedDailyHtml(),
            redeemFinalUrl = "https://example.com/mission/daily",
        )

        val result = runCatching { AccountRemoteDataSource(api, parser).checkIn() }

        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun checkIn_doesNotTreatExternalSignInPathAsAuthenticationExpiry() = runTest {
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml("84830"),
            dailyFinalUrl = "https://example.com/signin",
        )

        val result = runCatching { AccountRemoteDataSource(api, parser).checkIn() }

        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.Parse)
    }

    @Test
    fun checkIn_rejectsBrowserRiskPage() = runTest {
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml("84830"),
            redeemHtml = "请用一个干净安装的浏览器重试",
        )

        val result = runCatching { AccountRemoteDataSource(api, parser).checkIn() }

        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun checkIn_reportsExpiredAuthenticationBeforeRedeeming() = runTest {
        val api = FakeV2exRawApi(dailyHtml = "<form action='/signin'><input name='once'></form>")

        val result = runCatching { AccountRemoteDataSource(api, parser).checkIn() }

        assertThat(api.redeemCalls).isEqualTo(0)
        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.Auth)
    }

    @Test
    fun checkIn_rejectsRestrictedDailyRedirectBeforeRedeeming() = runTest {
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml("84830"),
            dailyFinalUrl = "https://www.v2ex.com/restricted",
        )

        val result = runCatching { AccountRemoteDataSource(api, parser).checkIn() }

        assertThat(api.redeemCalls).isEqualTo(0)
        assertThat((result.exceptionOrNull() as NodeFlowException).kind)
            .isEqualTo(NodeFlowException.Kind.AccessDenied)
    }

    @Test
    fun checkIn_doesNotSwallowCancellationWhileReadingReward() = runTest {
        val cancellation = CancellationException("test cancellation")
        val api = FakeV2exRawApi(
            dailyHtml = redeemableDailyHtml("84830"),
            redeemHtml = claimedDailyHtml(),
            balanceError = cancellation,
        )

        val error = runCatching { AccountRemoteDataSource(api, parser).checkIn() }.exceptionOrNull()

        assertThat(error).isSameInstanceAs(cancellation)
    }

    private class FakeV2exRawApi(
        private val homeHtml: String = "",
        private val dailyHtml: String = "",
        private val dailyFinalUrl: String? = "https://www.v2ex.com/mission/daily",
        private val balanceHtml: String = "",
        private val redeemHtml: String = "",
        private val redeemFinalUrl: String = "https://www.v2ex.com/mission/daily",
        private val balanceError: Throwable? = null,
    ) : V2exRawApi {
        var redeemCalls: Int = 0
            private set
        var lastRedeemOnce: String? = null
            private set
        var lastRedeemReferer: String? = null
            private set
        override suspend fun latestTopics(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topic(id: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun replies(topicId: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun node(name: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun member(username: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun recentTopicsHtml(page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun favoriteTopicsHtml(page: Int): Response<ResponseBody> = error("Unexpected favorites request")

        override suspend fun allTopicsHtml(): Response<ResponseBody> = htmlResponse("")

        override suspend fun planesHtml(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topicHtml(topicId: Long, page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun memberHtml(username: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun signInPage(next: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun captcha(cacheBust: Long, referer: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun signIn(
            fields: Map<String, String>,
            origin: String,
            referer: String,
        ): Response<ResponseBody> = htmlResponse("")

        override suspend fun signInTwoFactor(
            next: String,
            fields: Map<String, String>,
            referer: String,
        ): Response<ResponseBody> = htmlResponse("")

        override suspend fun home(): Response<ResponseBody> = htmlResponse(homeHtml)

        override suspend fun dailyMission(): Response<ResponseBody> = htmlResponse(dailyHtml, dailyFinalUrl)

        override suspend fun redeemDailyMission(once: String, referer: String): Response<ResponseBody> {
            redeemCalls += 1
            lastRedeemOnce = once
            lastRedeemReferer = referer
            return htmlResponse(redeemHtml, redeemFinalUrl)
        }

        override suspend fun balance(): Response<ResponseBody> {
            balanceError?.let { throw it }
            return htmlResponse(balanceHtml)
        }

        override suspend fun notifications(page: Int): Response<ResponseBody> = emptyResponse()

        override suspend fun notesHtml(): Response<ResponseBody> = emptyResponse()

        override suspend fun noteEditHtml(id: Long): Response<ResponseBody> = emptyResponse()

        override suspend fun noteEditSubmit(id: Long, content: String, syntax: String): Response<ResponseBody> =
            emptyResponse()

        override suspend fun noteNewSubmit(content: String, syntax: String): Response<ResponseBody> =
            emptyResponse()

        override suspend fun favoriteTopic(
            topicId: Long,
            once: String,
            referer: String
        ): Response<ResponseBody> =
            emptyResponse()

        override suspend fun unfavoriteTopic(
            topicId: Long,
            once: String,
            referer: String
        ): Response<ResponseBody> =
            emptyResponse()

        private fun emptyResponse(): Response<ResponseBody> = htmlResponse("")

        private fun htmlResponse(
            html: String,
            finalUrl: String? = null,
        ): Response<ResponseBody> {
            val body = html.toResponseBody("text/html".toMediaType())
            if (finalUrl == null) return Response.success(body)
            val raw = okhttp3.Response.Builder()
                .request(Request.Builder().url(finalUrl).build())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body)
                .build()
            return Response.success(body, raw)
        }
    }

    private fun redeemableDailyHtml(once: String) = """
        <div class="cell">
          <span>您已连续登录 8 天</span>
          <input type="button" onclick="location.href = '/mission/daily/redeem?once=$once';" value="领取奖励" />
        </div>
    """.trimIndent()

    private fun claimedDailyHtml() = """
        <div class="cell">每日登录奖励已领取</div>
        <input type="button" onclick="location.href = '/balance';" value="查看我的账户余额" />
    """.trimIndent()
}
