package app.mystery0.nodeflow.data.auth

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.core.network.V2exCookieJar
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.testing.TestV2exRawApi
import app.mystery0.nodeflow.testing.emptyHtmlResponse
import app.mystery0.nodeflow.testing.testHtmlResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class WebAuthRemoteDataSourceTest {
    private val parser = V2exHtmlParser()
    private val cookieJar = V2exCookieJar()

    @Test
    fun loginChallenge_readsDynamicFieldsAndCaptchaBytes() = runTest {
        val api = FakeV2exRawApi(
            signInPageHtml = """
                <form method="post" action="/signin">
                  <input type="text" class="sl" name="user_hash" placeholder="Username or Email" />
                  <input type="password" class="sl" name="pass_hash" />
                  <img id="captcha-image" src="/_captcha" alt="CAPTCHA">
                  <input type="text" class="sl" name="captcha_hash" placeholder="Enter the code above, click to change">
                  <input type="hidden" value="12345" name="once" />
                  <input type="hidden" value="/" name="next" />
                </form>
            """.trimIndent(),
            captchaBytes = byteArrayOf(1, 2, 3),
        )
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val challenge = dataSource.loginChallenge()

        assertThat(challenge.usernameField).isEqualTo("user_hash")
        assertThat(challenge.passwordField).isEqualTo("pass_hash")
        assertThat(challenge.captchaField).isEqualTo("captcha_hash")
        assertThat(challenge.once).isEqualTo("12345")
        assertThat(challenge.next).isEqualTo("/mission/daily")
        assertThat(challenge.captchaImageBytes.toList()).containsExactly(1.toByte(), 2.toByte(), 3.toByte()).inOrder()
        assertThat(api.lastSignInPageNext).isEqualTo("/mission/daily")
    }

    @Test
    fun login_returnsSessionWhenPostRedirectPageIsLoggedIn() = runTest {
        val api = FakeV2exRawApi(
            signInResponseHtml = """
                <div id="Main"><a href="/member/topicAuthor">topicAuthor</a></div>
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <img src="//cdn.v2ex.com/avatar/current_normal.png" />
                  <a href="/signout?once=12345">Sign Out</a>
                </div>
            """.trimIndent(),
        )
        cookieJar.saveFromResponse(
            "https://www.v2ex.com/".toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .domain("www.v2ex.com")
                    .path("/")
                    .name("A2")
                    .value("auth")
                    .build(),
            ),
        )
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val result = dataSource.login(
            username = "currentUser",
            password = "sample-password",
            captcha = "ABCD",
            challenge = sampleChallenge(),
        )
        val session = (result as AuthLoginResult.Completed).session

        assertThat(session.username).isEqualTo("currentUser")
        assertThat(session.cookieHeader).isEqualTo("A2=auth")
        assertThat(api.lastSignInFields).containsEntry("user_field", "currentUser")
        assertThat(api.lastSignInFields).containsEntry("captcha_field", "ABCD")
        assertThat(api.lastSignInFields).containsEntry("next", "/mission/daily")
        assertThat(api.homeRequestCount).isEqualTo(0)
    }

    @Test
    fun login_acceptsEmailInputWhenPostRedirectPageContainsUsername() = runTest {
        val api = FakeV2exRawApi(
            signInResponseHtml = """
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <img src="//cdn.v2ex.com/avatar/current_normal.png" />
                  <a href="/signout?once=12345">Sign Out</a>
                </div>
            """.trimIndent(),
        )
        cookieJar.saveFromResponse(
            "https://www.v2ex.com/".toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .domain("www.v2ex.com")
                    .path("/")
                    .name("A2")
                    .value("auth")
                    .build(),
            ),
        )
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val result = dataSource.login(
            username = "current@example.com",
            password = "sample-password",
            captcha = "ABCD",
            challenge = sampleChallenge(),
        )
        val session = (result as AuthLoginResult.Completed).session

        assertThat(session.username).isEqualTo("currentUser")
        assertThat(session.cookieHeader).isEqualTo("A2=auth")
    }

    @Test
    fun login_returnsTwoFactorRequiredWhenPostRedirectPageIsTwoFactorForm() = runTest {
        val api = FakeV2exRawApi(
            signInResponseHtml = """
                <form method="post" action="/2fa?next=/mission/daily">
                  <table>
                    <tr><td>两步验证</td></tr>
                    <tr><td><input type="hidden" name="once" value="24680" /></td></tr>
                  </table>
                </form>
            """.trimIndent(),
        )
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val result = dataSource.login(
            username = "currentUser",
            password = "sample-password",
            captcha = "ABCD",
            challenge = sampleChallenge(),
        )

        val challenge = (result as AuthLoginResult.TwoFactorRequired).challenge
        assertThat(challenge.once).isEqualTo("24680")
        assertThat(challenge.title).contains("两步验证")
    }

    @Test
    fun login_prioritizesTwoFactorFormOverAccountMarkers() = runTest {
        val api = FakeV2exRawApi(signInResponseHtml = twoFactorPageWithAccountMarkers())
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val result = dataSource.login(
            username = "currentUser",
            password = "sample-password",
            captcha = "ABCD",
            challenge = sampleChallenge(),
        )

        assertThat(result).isEqualTo(
            AuthLoginResult.TwoFactorRequired(TwoFactorChallenge(once = "24680", title = "两步验证")),
        )
    }

    @Test
    fun verifyTwoFactor_returnsSessionWhenCodeAccepted() = runTest {
        val api = FakeV2exRawApi(
            twoFactorResponseHtml = """
                <div id="Rightbar">
                  <a href="/member/currentUser">currentUser</a>
                  <img src="//cdn.v2ex.com/avatar/current_normal.png" />
                  <a href="/signout?once=12345">Sign Out</a>
                </div>
            """.trimIndent(),
        )
        cookieJar.saveFromResponse(
            "https://www.v2ex.com/".toHttpUrl(),
            listOf(
                Cookie.Builder()
                    .domain("www.v2ex.com")
                    .path("/")
                    .name("A2")
                    .value("auth")
                    .build(),
            ),
        )
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val session = dataSource.verifyTwoFactor(
            code = "123456",
            challenge = TwoFactorChallenge(once = "24680", title = "两步验证"),
        )

        assertThat(session.username).isEqualTo("currentUser")
        assertThat(session.cookieHeader).isEqualTo("A2=auth")
        assertThat(api.lastTwoFactorFields).containsEntry("once", "24680")
        assertThat(api.lastTwoFactorFields).containsEntry("code", "123456")
    }

    @Test
    fun verifyTwoFactor_rejectsRemainingChallengeDespiteAccountMarkers() = runTest {
        val api = FakeV2exRawApi(twoFactorResponseHtml = twoFactorPageWithAccountMarkers())
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val error = runCatching {
            dataSource.verifyTwoFactor(
                code = "123456",
                challenge = TwoFactorChallenge(once = "24680", title = "两步验证"),
            )
        }.exceptionOrNull() as? NodeFlowException

        assertThat(error?.kind).isEqualTo(NodeFlowException.Kind.Auth)
        assertThat(error?.message).isEqualTo("两步验证码错误，请重试")
    }

    @Test
    fun login_throwsAuthErrorWhenRedirectedHomeIsAnonymous() = runTest {
        val api = FakeV2exRawApi(
            signInResponseHtml = """<a href="/signin">Sign In</a>""",
        )
        val dataSource = WebAuthRemoteDataSource(api, parser, cookieJar)

        val result = runCatching {
            dataSource.login(
                username = "currentUser",
                password = "sample-password",
                captcha = "ABCD",
                challenge = sampleChallenge(),
            )
        }

        val error = result.exceptionOrNull() as NodeFlowException
        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Auth)
    }

    private fun sampleChallenge(): LoginChallenge = LoginChallenge(
        usernameField = "user_field",
        passwordField = "password_field",
        captchaField = "captcha_field",
        once = "12345",
        next = "/mission/daily",
        captchaPath = "/_captcha",
        captchaImageBytes = byteArrayOf(),
    )

    private fun twoFactorPageWithAccountMarkers(): String {
        cookieJar.saveFromResponse(
            "https://www.v2ex.com/".toHttpUrl(),
            listOf(Cookie.Builder().hostOnlyDomain("www.v2ex.com").name("pending").value("sample").build()),
        )
        return """
            <div id="Top">
              <a href="/member/currentUser">currentUser</a>
              <a href="/signout?once=12345">Sign Out</a>
            </div>
            <form method="post" action="/2fa?next=/mission/daily">
              <h1>两步验证</h1>
              <input type="text" name="code">
              <input type="hidden" name="once" value="24680">
            </form>
        """.trimIndent()
    }

    private class FakeV2exRawApi(
        private val signInPageHtml: String = "",
        private val captchaBytes: ByteArray = byteArrayOf(),
        private val signInResponseHtml: String = "",
        private val twoFactorResponseHtml: String = "",
        private val homeHtml: String = "",
    ) : TestV2exRawApi(fallback = { emptyHtmlResponse() }) {
        var lastSignInPageNext: String? = null
        var lastSignInFields: Map<String, String> = emptyMap()
        var lastTwoFactorFields: Map<String, String> = emptyMap()
        var homeRequestCount: Int = 0

        override suspend fun signInPage(next: String): Response<ResponseBody> {
            lastSignInPageNext = next
            return testHtmlResponse(signInPageHtml)
        }

        override suspend fun captcha(cacheBust: Long, referer: String): Response<ResponseBody> =
            Response.success(captchaBytes.toResponseBody("image/png".toMediaType()))

        override suspend fun signIn(
            fields: Map<String, String>,
            origin: String,
            referer: String,
        ): Response<ResponseBody> {
            lastSignInFields = fields
            return testHtmlResponse(signInResponseHtml)
        }

        override suspend fun home(userAgent: String): Response<ResponseBody> {
            homeRequestCount += 1
            return testHtmlResponse(homeHtml)
        }

        override suspend fun signInTwoFactor(
            next: String,
            fields: Map<String, String>,
            referer: String,
        ): Response<ResponseBody> {
            lastTwoFactorFields = fields
            return testHtmlResponse(twoFactorResponseHtml)
        }
    }
}
