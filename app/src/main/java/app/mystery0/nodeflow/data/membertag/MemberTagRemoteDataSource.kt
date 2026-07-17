package app.mystery0.nodeflow.data.membertag

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.network.bodyStringOrThrow
import app.mystery0.nodeflow.core.network.safeNetworkCall
import app.mystery0.nodeflow.core.parser.PolishMemberTagParser
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import okhttp3.ResponseBody
import retrofit2.Response

class MemberTagRemoteDataSource(
    private val api: V2exRawApi,
    private val parser: V2exHtmlParser,
) {
    /** 拉取并解析 Polish 用户标签；未安装插件/无 note 返回空映射。 */
    suspend fun fetchMemberTags(): Map<String, List<String>> = safeNetworkCall {
        val notesHtml = api.notesHtml().guardedHtml("/notes")
        val noteId = parser.parsePolishNoteId(notesHtml) ?: return@safeNetworkCall emptyMap()
        val editHtml = api.noteEditHtml(noteId).guardedHtml("/notes/edit/$noteId")
        val content = parser.parseNoteEditContent(editHtml) ?: return@safeNetworkCall emptyMap()
        PolishMemberTagParser.parse(content)
    }

    /** 校验最终地址与页面状态，登录页/受限页不当业务内容。 */
    private fun Response<ResponseBody>.guardedHtml(expectedPath: String): String {
        val url = raw().request.url
        if (url.scheme == "https" && url.host == "www.v2ex.com" && url.encodedPath == "/signin") {
            throw authInvalid()
        }
        if (url.scheme != "https" || url.host != "www.v2ex.com" || url.encodedPath != expectedPath) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.Parse,
                message = "记事本页面地址异常",
            )
        }
        val html = bodyStringOrThrow()
        if (parser.hasSignInEntry(html)) throw authInvalid()
        if (parser.hasAccessChallenge(html)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.AccessDenied,
                message = "V2EX 暂时拒绝访问记事本",
            )
        }
        return html
    }

    private fun authInvalid() = NodeFlowException(
        kind = NodeFlowException.Kind.Auth,
        message = "登录状态已失效，请重新登录",
    )
}
