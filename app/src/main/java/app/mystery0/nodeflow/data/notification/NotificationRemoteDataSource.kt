package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.V2exHtmlParser

class NotificationRemoteDataSource(
    private val api: V2exRawApi,
    private val parser: V2exHtmlParser,
) {
    suspend fun notifications(page: Int): List<Notification> = safeNetworkCall {
        val response = api.notifications(page)
        val url = response.raw().request.url
        if (url.scheme == "https" && url.host == "www.v2ex.com") {
            when (url.encodedPath) {
                "/signin" -> throw NodeFlowException(
                    kind = NodeFlowException.Kind.Auth,
                    message = "登录状态已失效，请重新登录",
                )
                "/restricted" -> throw accessDenied()
            }
        }
        if (url.scheme != "https" || url.host != "www.v2ex.com" || url.encodedPath != "/notifications") {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "通知页面地址异常，请稍后重试",
            )
        }
        val html = response.bodyStringOrThrow()
        if (parser.hasSignInEntry(html)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Auth,
                message = "登录状态已失效，请重新登录",
            )
        }
        if (parser.hasAccessChallenge(html)) throw accessDenied()
        if (!parser.isNotificationsPage(html)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "通知页面结构异常，请稍后重试",
            )
        }
        parser.parseNotifications(html)
    }

    private fun accessDenied() = NodeFlowException(
        kind = NodeFlowException.Kind.AccessDenied,
        message = "V2EX 暂时拒绝访问通知，请稍后重试",
    )
}
