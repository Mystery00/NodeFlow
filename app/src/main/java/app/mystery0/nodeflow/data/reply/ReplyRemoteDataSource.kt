package app.mystery0.nodeflow.data.reply

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exWriteApi
import app.mystery0.nodeflow.core.network.asOneShot
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.domain.reply.CreateReplyResult
import app.mystery0.nodeflow.domain.reply.ReplyConstraints
import app.mystery0.nodeflow.domain.reply.ReplyFailureReason
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

class ReplyRemoteDataSource(
    private val api: V2exWriteApi,
    private val parser: V2exHtmlParser,
    private val baseUrl: HttpUrl = "https://www.v2ex.com/".toHttpUrl(),
) {
    suspend fun loadConstraints(topicId: Long): ReplyConstraints {
        val html = getTopicPage(topicId)
        val form = parser.parseReplyForm(topicId, html, baseUrl.toString())
            ?: throw parseError("当前主题无法回复")
        return ReplyConstraints(form.maxLength)
    }

    suspend fun createReply(
        topicId: Long,
        content: String,
    ): CreateReplyResult {
        val topicUrl = baseUrl.resolve("t/$topicId") ?: return parseFailure()
        val beforeHtml = getTopicPage(topicId)
        val form = parser.parseReplyForm(topicId, beforeHtml, baseUrl.toString())
            ?: return failure(ReplyFailureReason.TopicUnavailable, "当前主题无法回复")
        val action = runCatching { form.actionUrl.toHttpUrl() }.getOrNull()
            ?: return parseFailure()
        if (action.scheme != baseUrl.scheme || action.host != baseUrl.host ||
            action.port != baseUrl.port || action.encodedPath != "/t/$topicId"
        ) return parseFailure()

        val beforeReplies = loadLastReplies(topicId, beforeHtml)
            ?: return failure(ReplyFailureReason.Parse, "无法确认回复列表，已取消提交")
        val nextFloor = (beforeReplies.maxOfOrNull { it.floor } ?: 0) + 1
        val requestBody = FormBody.Builder().apply {
            (form.hiddenFields + (form.contentField to content)).forEach { (name, value) ->
                add(name, value)
            }
        }.build().asOneShot()
        val response = api.submitForm(
            url = action.toString(),
            body = requestBody,
            origin = baseUrl.newBuilder().encodedPath("/").build().toString().trimEnd('/'),
            referer = topicUrl.toString(),
        )
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            return failure(ReplyFailureReason.Server, "回复失败：HTTP ${response.code()}")
        }
        response.body()?.close()
        return CreateReplyResult.Success(nextFloor)
    }

    private suspend fun getTopicPage(topicId: Long): String {
        val response = api.getHtml(baseUrl.resolve("t/$topicId").toString())
        val finalUrl = response.raw().request.url
        if (finalUrl.encodedPath == "/signin") throw authError()
        val html = response.bodyStringOrThrow()
        if (parser.hasSignInEntry(html)) throw authError()
        if (parser.hasAccessChallenge(html)) throw parseError("V2EX 暂时拒绝访问主题")
        return html
    }

    private suspend fun loadLastReplies(topicId: Long, initialHtml: String) =
        parser.parseTopicHtml(topicId, initialHtml)?.let { parsed ->
            if (parsed.pageCount <= 1) {
                parsed.replies
            } else {
                val pageUrl = baseUrl.resolve("t/$topicId?p=${parsed.pageCount}").toString()
                val response = api.getHtml(pageUrl)
                val finalUrl = response.raw().request.url
                if (finalUrl.scheme != baseUrl.scheme || finalUrl.host != baseUrl.host ||
                    finalUrl.port != baseUrl.port || finalUrl.encodedPath != "/t/$topicId"
                ) return@let null
                val html = response.bodyStringOrThrow()
                if (finalUrl.encodedPath == "/signin" || parser.hasSignInEntry(html)) {
                    throw authError()
                }
                if (parser.hasAccessChallenge(html)) {
                    throw parseError("V2EX 暂时拒绝访问主题")
                }
                parser.parseTopicHtml(topicId, html)?.replies
            }
        }

    private fun failure(reason: ReplyFailureReason, message: String) =
        CreateReplyResult.Failure(reason, message)

    private fun parseFailure() = failure(ReplyFailureReason.Parse, "回复响应无法识别")

    private fun parseError(message: String) = NodeFlowException(
        kind = NodeFlowException.Kind.Parse,
        message = message,
    )

    private fun authError() = NodeFlowException(
        kind = NodeFlowException.Kind.Auth,
        message = "登录状态已失效，请重新登录",
    )

}
