package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.parser.hasRestrictedSignInForm
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import retrofit2.Response

enum class V2exHtmlAccessTarget {
    Topic,
    NodeTopics,
}

const val V2EX_ACCESS_DENIED_MESSAGE =
    "当前账号无权访问此内容；如果尚未登录，请登录具有访问权限的账号后重试。"

fun Response<ResponseBody>.accessibleHtmlOrThrow(
    target: V2exHtmlAccessTarget,
): String {
    val finalPath = raw().request.url.encodedPath
    val deniedByUrl =
        finalPath == "/restricted" ||
            finalPath == "/signin" ||
            (target == V2exHtmlAccessTarget.NodeTopics && finalPath == "/")
    if (deniedByUrl) throw accessDenied()

    val html = bodyStringOrThrow()
    val deniedByDom = Jsoup.parse(html).hasRestrictedSignInForm()
    if (deniedByDom) throw accessDenied()
    return html
}

private fun accessDenied(): NodeFlowException =
    NodeFlowException(
        kind = NodeFlowException.Kind.AccessDenied,
        message = V2EX_ACCESS_DENIED_MESSAGE,
    )
