package app.mystery0.nodeflow.data.auth

import app.mystery0.nodeflow.core.datastore.SessionStore
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.core.network.V2exCookieJar
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
import app.mystery0.nodeflow.core.security.SecretCipher
import app.mystery0.nodeflow.testing.FakeStringKeyValueStorage
import app.mystery0.nodeflow.testing.TestV2exRawApi
import app.mystery0.nodeflow.testing.testHtmlResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Test
import retrofit2.Response

class AuthRepositoryImplTest {
    private val dispatcher = StandardTestDispatcher()

    @Test
    fun login_completedSavesSessionAndUpdatesSessionFlow() = runTest(dispatcher) {
        val sessionStore = createSessionStore()
        val cookieJar = V2exCookieJar().apply {
            saveFromResponse(V2EX_URL, listOf(validCookie("A2", "initial-a2")))
        }
        val api = FakeAuthRawApi(
            signInHtml = LOGGED_IN_HTML,
            onSignIn = {
                cookieJar.saveFromResponse(V2EX_URL, listOf(validCookie("A2", "logged-in-a2")))
            },
        )
        val repository = createRepository(sessionStore, cookieJar, api)

        val challenge = repository.loginChallenge().getOrThrow()
        assertThat(cookieJar.cookieHeader(V2EX_URL)).isEmpty()

        val result = repository.login("alice", "secret", "1234", challenge).getOrThrow()

        assertThat(result).isInstanceOf(AuthLoginResult.Completed::class.java)
        val savedSession = sessionStore.session.first()
        assertThat(savedSession.username).isEqualTo("alice")
        assertThat(savedSession.cookieHeader).contains("A2=logged-in-a2")
    }

    @Test
    fun login_twoFactorRequiredDoesNotPersistSessionUntilVerified() = runTest(dispatcher) {
        val sessionStore = createSessionStore()
        val cookieJar = V2exCookieJar()
        val api = FakeAuthRawApi(
            signInHtml = TWO_FACTOR_HTML,
            twoFactorHtml = LOGGED_IN_HTML,
            onSignIn = {
                cookieJar.saveFromResponse(V2EX_URL, listOf(validCookie("A2", "pending-a2")))
            },
            onTwoFactor = {
                cookieJar.saveFromResponse(V2EX_URL, listOf(validCookie("A2", "verified-a2")))
            },
        )
        val repository = createRepository(sessionStore, cookieJar, api)

        val loginResult = repository.login("alice", "secret", "1234", TEST_CHALLENGE).getOrThrow()

        assertThat(loginResult).isInstanceOf(AuthLoginResult.TwoFactorRequired::class.java)
        assertThat(sessionStore.session.first()).isEqualTo(AuthSession())

        val twoFactorChallenge = (loginResult as AuthLoginResult.TwoFactorRequired).challenge
        val verifiedSession = repository.verifyTwoFactor("654321", twoFactorChallenge).getOrThrow()

        assertThat(verifiedSession.username).isEqualTo("alice")
        assertThat(verifiedSession.cookieHeader).contains("A2=verified-a2")
        assertThat(sessionStore.session.first()).isEqualTo(verifiedSession)
    }

    @Test
    fun clearSession_clearsCookiesSessionStoreAndMemberTags() = runTest(dispatcher) {
        val sessionStore = createSessionStore().apply {
            save(AuthSession(cookieHeader = "A2=active", username = "alice"))
        }
        val cookieJar = V2exCookieJar().apply {
            saveFromResponse(V2EX_URL, listOf(validCookie("A2", "active")))
        }
        var memberTagsCleared = 0
        val repository = createRepository(
            sessionStore = sessionStore,
            cookieJar = cookieJar,
            api = FakeAuthRawApi(),
            clearMemberTags = { memberTagsCleared += 1 },
        )

        repository.clearSession()

        assertThat(cookieJar.cookieHeader(V2EX_URL)).isEmpty()
        assertThat(sessionStore.session.first()).isEqualTo(AuthSession())
        assertThat(memberTagsCleared).isEqualTo(1)
    }

    private fun createSessionStore(): SessionStore =
        SessionStore(EncryptedKeyValueStore(FakeStringKeyValueStorage(), IdentityCipher))

    private fun createRepository(
        sessionStore: SessionStore,
        cookieJar: V2exCookieJar,
        api: FakeAuthRawApi,
        clearMemberTags: suspend () -> Unit = {},
    ): AuthRepositoryImpl = AuthRepositoryImpl(
        sessionStore = sessionStore,
        webAuthRemoteDataSource = WebAuthRemoteDataSource(api, V2exHtmlParser(), cookieJar),
        cookieJar = cookieJar,
        clearMemberTags = clearMemberTags,
        ioDispatcher = dispatcher,
    )

    private class FakeAuthRawApi(
        private val signInHtml: String = LOGGED_IN_HTML,
        private val twoFactorHtml: String = LOGGED_IN_HTML,
        private val onSignIn: () -> Unit = {},
        private val onTwoFactor: () -> Unit = {},
    ) : TestV2exRawApi() {
        override suspend fun signInPage(next: String): Response<ResponseBody> = testHtmlResponse(SIGN_IN_FORM_HTML)

        override suspend fun captcha(cacheBust: Long, referer: String): Response<ResponseBody> =
            Response.success(byteArrayOf(1, 2, 3).toResponseBody("image/png".toMediaType()))

        override suspend fun signIn(
            fields: Map<String, String>,
            origin: String,
            referer: String,
        ): Response<ResponseBody> {
            onSignIn()
            return testHtmlResponse(signInHtml)
        }

        override suspend fun signInTwoFactor(
            next: String,
            fields: Map<String, String>,
            referer: String,
        ): Response<ResponseBody> {
            onTwoFactor()
            return testHtmlResponse(twoFactorHtml)
        }
    }

    private object IdentityCipher : SecretCipher {
        override fun encrypt(plainText: String): String = plainText
        override fun decrypt(payload: String): String = payload
    }

    private companion object {
        val V2EX_URL = "https://www.v2ex.com/".toHttpUrl()
        val TEST_CHALLENGE = LoginChallenge(
            usernameField = "u",
            passwordField = "p",
            captchaField = "c",
            once = "12345",
            next = "/mission/daily",
            captchaPath = "/_captcha",
            captchaImageBytes = byteArrayOf(1),
        )
        const val SIGN_IN_FORM_HTML = """
            <form method="post" action="/signin">
              <input type="text" class="sl" name="u" placeholder="Username or Email" />
              <input type="password" class="sl" name="p" />
              <img id="captcha-image" src="/_captcha" alt="CAPTCHA">
              <input type="text" class="sl" name="c" placeholder="Enter the code above, click to change">
              <input type="hidden" value="12345" name="once" />
              <input type="hidden" value="/" name="next" />
            </form>
        """
        const val LOGGED_IN_HTML = """
            <div id="Rightbar">
              <a href="/member/alice">alice</a>
              <img src="//cdn.v2ex.com/avatar/alice.png" />
              <a href="/signout?once=12345">Sign Out</a>
            </div>
        """
        const val TWO_FACTOR_HTML = """
            <form method="post" action="/2fa">
              <div class="header">两步验证</div>
              <input type="hidden" value="67890" name="once" />
              <input type="text" class="sl" name="code" />
            </form>
        """

        fun validCookie(name: String, value: String): Cookie = Cookie.Builder()
            .domain("www.v2ex.com")
            .path("/")
            .name(name)
            .value(value)
            .build()
    }
}
