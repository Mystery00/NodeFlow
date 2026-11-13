package app.mystery0.nodeflow.testing

import app.mystery0.nodeflow.core.network.V2exRawApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.Response
import okhttp3.Response as OkHttpResponse

/** 测试用可覆写 V2exRawApi 基类，默认抛出明确错误，也可注入 fallback 处理未覆写接口。 */
open class TestV2exRawApi(
    private val fallback: ((String) -> Response<ResponseBody>)? = null,
) : V2exRawApi {
    override suspend fun topic(id: Long): Response<ResponseBody> = default("topic")
    override suspend fun replies(topicId: Long): Response<ResponseBody> = default("replies")
    override suspend fun node(name: String): Response<ResponseBody> = default("node")
    override suspend fun member(username: String): Response<ResponseBody> = default("member")
    override suspend fun nodeTopicsHtml(nodeName: String, page: Int?): Response<ResponseBody> = default("nodeTopicsHtml")
    override suspend fun recentTopicsHtml(page: Int?): Response<ResponseBody> = default("recentTopicsHtml")
    override suspend fun allTopicsHtml(): Response<ResponseBody> = default("allTopicsHtml")
    override suspend fun favoriteTopicsHtml(page: Int): Response<ResponseBody> = default("favoriteTopicsHtml")
    override suspend fun planesHtml(): Response<ResponseBody> = default("planesHtml")
    override suspend fun topicHtml(topicId: Long, page: Int?): Response<ResponseBody> = default("topicHtml")
    override suspend fun memberHtml(username: String): Response<ResponseBody> = default("memberHtml")
    override suspend fun signInPage(next: String): Response<ResponseBody> = default("signInPage")
    override suspend fun captcha(cacheBust: Long, referer: String): Response<ResponseBody> = default("captcha")
    override suspend fun signIn(fields: Map<String, String>, origin: String, referer: String): Response<ResponseBody> = default("signIn")
    override suspend fun signInTwoFactor(next: String, fields: Map<String, String>, referer: String): Response<ResponseBody> = default("signInTwoFactor")
    override suspend fun home(userAgent: String): Response<ResponseBody> = default("home")
    override suspend fun dailyMission(): Response<ResponseBody> = default("dailyMission")
    override suspend fun redeemDailyMission(once: String, referer: String): Response<ResponseBody> = default("redeemDailyMission")
    override suspend fun balance(): Response<ResponseBody> = default("balance")
    override suspend fun notifications(page: Int): Response<ResponseBody> = default("notifications")
    override suspend fun notesHtml(): Response<ResponseBody> = default("notesHtml")
    override suspend fun noteEditHtml(id: Long): Response<ResponseBody> = default("noteEditHtml")
    override suspend fun noteEditSubmit(id: Long, content: String, syntax: String): Response<ResponseBody> = default("noteEditSubmit")
    override suspend fun noteNewSubmit(content: String, syntax: String): Response<ResponseBody> = default("noteNewSubmit")
    override suspend fun favoriteTopic(topicId: Long, once: String, referer: String): Response<ResponseBody> = default("favoriteTopic")
    override suspend fun unfavoriteTopic(topicId: Long, once: String, referer: String): Response<ResponseBody> = default("unfavoriteTopic")

    private fun default(name: String): Response<ResponseBody> =
        fallback?.invoke(name) ?: error("Unexpected V2exRawApi call: $name")
}

internal fun emptyHtmlResponse(): Response<ResponseBody> =
    Response.success("".toResponseBody("text/html".toMediaType()))

internal fun testHtmlResponse(html: String, finalUrl: String? = null): Response<ResponseBody> {
    val body = html.toResponseBody("text/html".toMediaType())
    if (finalUrl == null) return Response.success(body)
    val raw = OkHttpResponse.Builder()
        .request(Request.Builder().url(finalUrl).build())
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .build()
    return Response.success(body, raw)
}

internal fun testJsonResponse(body: String): Response<ResponseBody> =
    Response.success(body.toResponseBody("application/json".toMediaType()))
