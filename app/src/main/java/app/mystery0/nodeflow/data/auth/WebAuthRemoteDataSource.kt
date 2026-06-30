package app.mystery0.nodeflow.data.auth

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.AuthLoginResult
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.LoginChallenge
import app.mystery0.nodeflow.core.model.TwoFactorChallenge
import app.mystery0.nodeflow.core.network.V2exCookieJar
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyBytesOrThrow
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import okhttp3.HttpUrl.Companion.toHttpUrl

class WebAuthRemoteDataSource(
    private val api: V2exRawApi,
    private val parser: V2exHtmlParser,
    private val cookieJar: V2exCookieJar,
) {
    suspend fun loginChallenge(): LoginChallenge = safeNetworkCall {
        cookieJar.clear()
        val parsed = parser.parseSignInChallenge(api.signInPage(LOGIN_NEXT).bodyStringOrThrow())
            ?: throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "无法解析登录表单",
            )
        val captchaBytes = api.captcha(
            cacheBust = System.currentTimeMillis(),
            referer = SIGN_IN_REFERER,
        ).bodyBytesOrThrow()
        LoginChallenge(
            usernameField = parsed.usernameField,
            passwordField = parsed.passwordField,
            captchaField = parsed.captchaField,
            once = parsed.once,
            next = LOGIN_NEXT,
            captchaPath = parsed.captchaPath,
            captchaImageBytes = captchaBytes,
        )
    }

    suspend fun login(
        username: String,
        password: String,
        captcha: String,
        challenge: LoginChallenge,
    ): AuthLoginResult = safeNetworkCall {
        val fields = mapOf(
            challenge.usernameField to username,
            challenge.passwordField to password,
            challenge.captchaField to captcha,
            "once" to challenge.once,
            "next" to LOGIN_NEXT,
        )
        val responseHtml = api.signIn(
            fields = fields,
            referer = SIGN_IN_REFERER,
        ).bodyStringOrThrow()
        parseLoginResult(responseHtml)
    }

    suspend fun verifyTwoFactor(
        code: String,
        challenge: TwoFactorChallenge,
    ): AuthSession = safeNetworkCall {
        val responseHtml = api.signInTwoFactor(
            fields = mapOf(
                "once" to challenge.once,
                "code" to code,
            ),
        ).bodyStringOrThrow()
        parseCompletedSession(responseHtml)
            ?: throw NodeFlowException(
                kind = NodeFlowException.Kind.Auth,
                message = parser.parseLoginProblem(responseHtml) ?: "两步验证码错误，请重试",
            )
    }

    private fun parseLoginResult(html: String): AuthLoginResult {
        parseCompletedSession(html)?.let { session ->
            return AuthLoginResult.Completed(session)
        }
        parser.parseTwoFactorChallenge(html)?.let { challenge ->
            return AuthLoginResult.TwoFactorRequired(
                TwoFactorChallenge(
                    once = challenge.once,
                    title = challenge.title,
                ),
            )
        }
        throw NodeFlowException(
            kind = NodeFlowException.Kind.Auth,
            message = parser.parseLoginProblem(html) ?: "登录失败，请检查用户名、密码和验证码",
        )
    }

    private fun parseCompletedSession(html: String): AuthSession? {
        val account = parser.parseLoginAccount(html) ?: return null
        val cookieHeader = cookieJar.cookieHeader(V2EX_HOME_URL)
        if (cookieHeader.isBlank()) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Auth,
                message = "登录成功但未获取到 Cookie",
            )
        }
        return AuthSession(
            cookieHeader = cookieHeader,
            username = account.username,
        )
    }

    private companion object {
        const val LOGIN_NEXT = "/mission/daily"
        const val SIGN_IN_REFERER = "https://www.v2ex.com/signin?next=/mission/daily"
        val V2EX_HOME_URL = "https://www.v2ex.com/".toHttpUrl()
    }
}
