package app.mystery0.nodeflow.data.account

import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import okhttp3.ResponseBody
import retrofit2.Response

/** 仅消费可信来源的新鲜页面响应，不因活跃度增加请求或接受重定向后的其他页面。 */
internal suspend fun AccountActivityStore?.updateFromPage(
    token: AccountActivityStore.RequestToken?,
    response: Response<ResponseBody>,
    html: String,
    expectedPath: String,
    parser: V2exHtmlParser,
) {
    if (this == null) return
    val raw = response.raw()
    val url = raw.request.url
    if (!response.isSuccessful || url.scheme != "https" || url.host != "www.v2ex.com" ||
        url.port != 443 || url.encodedPath != expectedPath ||
        (raw.cacheResponse != null && raw.networkResponse == null)
    ) return
    update(token, parser.parseAccountActivityPercent(html))
}
