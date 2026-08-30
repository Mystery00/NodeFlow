package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.notification.NotificationUnreadCountProvider
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser

class NotificationReminderRemoteDataSource(
    private val api: V2exRawApi,
    private val parser: V2exHtmlParser,
    private val expectedHost: String = V2EX_HOST,
    private val expectedScheme: String = "https",
) : NotificationUnreadCountProvider {
    override suspend fun unreadCount(): Int = safeNetworkCall {
        val response = api.home()
        val url = response.raw().request.url
        when {
            url.scheme == expectedScheme && url.host == expectedHost && url.encodedPath == "/signin" ->
                throw authExpired()
            url.scheme == expectedScheme && url.host == expectedHost && url.encodedPath == "/restricted" ->
                throw accessDenied()
            url.scheme != expectedScheme || url.host != expectedHost || url.encodedPath != "/" ->
                throw NodeFlowException(
                    kind = NodeFlowException.Kind.Parse,
                    message = "首页地址异常，请稍后重试",
                )
        }
        val html = response.bodyStringOrThrow()
        if (parser.hasSignInEntry(html)) throw authExpired()
        if (parser.hasAccessChallenge(html)) throw accessDenied()
        parser.parseUnreadNotificationCount(html)
            ?: throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "首页结构异常，请稍后重试",
            )
    }

    private fun authExpired() = NodeFlowException(
        kind = NodeFlowException.Kind.Auth,
        message = "登录状态已失效，请重新登录",
    )

    private fun accessDenied() = NodeFlowException(
        kind = NodeFlowException.Kind.AccessDenied,
        message = "V2EX 暂时拒绝访问首页，请稍后重试",
    )

    private companion object {
        const val V2EX_HOST = "www.v2ex.com"
    }
}
