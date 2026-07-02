package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
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

    private class FakeV2exRawApi(
        private val homeHtml: String = "",
        private val dailyHtml: String = "",
        private val balanceHtml: String = "",
    ) : V2exRawApi {
        override suspend fun latestTopics(): Response<ResponseBody> = htmlResponse("")

        override suspend fun topic(id: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun replies(topicId: Long): Response<ResponseBody> = htmlResponse("")

        override suspend fun node(name: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun member(username: String): Response<ResponseBody> = htmlResponse("")

        override suspend fun nodeTopicsHtml(nodeName: String, page: Int?): Response<ResponseBody> = htmlResponse("")

        override suspend fun recentTopicsHtml(page: Int?): Response<ResponseBody> = htmlResponse("")

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

        override suspend fun dailyMission(): Response<ResponseBody> = htmlResponse(dailyHtml)

        override suspend fun balance(): Response<ResponseBody> = htmlResponse(balanceHtml)

        private fun htmlResponse(html: String): Response<ResponseBody> =
            Response.success(html.toResponseBody("text/html".toMediaType()))
    }
}
