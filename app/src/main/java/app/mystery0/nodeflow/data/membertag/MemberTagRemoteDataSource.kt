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

    /**
     * 写回指定用户的标签：保存时实时读取记事内容做读-改-写，提交后回读校验。
     * 内容前缀/JSON 异常一律中止不提交；返回补丁后的完整标签映射。
     */
    suspend fun updateMemberTags(
        username: String,
        tags: List<String>,
        avatarUrl: String?,
    ): Map<String, List<String>> = safeNetworkCall {
        val notesHtml = api.notesHtml().guardedHtml("/notes")
        val noteId = parser.parsePolishNoteId(notesHtml)
            ?: return@safeNetworkCall createNoteWithTags(username, tags, avatarUrl)
        val editHtml = api.noteEditHtml(noteId).guardedHtml("/notes/edit/$noteId")
        val content = parser.parseNoteEditContent(editHtml)
            ?: throw contentBroken()
        val patched = PolishMemberTagParser.patch(content, username, tags, avatarUrl)
            ?: throw contentBroken()
        api.noteEditSubmit(noteId, patched).guardedSubmit()
        verifySaved(noteId, username, tags)
    }

    /** 账号没有 Polish 记事：创建仅含 member-tag 的最小记事；空标签无事可做。 */
    private suspend fun createNoteWithTags(
        username: String,
        tags: List<String>,
        avatarUrl: String?,
    ): Map<String, List<String>> {
        val initial = PolishMemberTagParser.buildInitial(username, tags, avatarUrl)
            ?: return emptyMap()
        api.noteNewSubmit(initial).guardedSubmit()
        val notesHtml = api.notesHtml().guardedHtml("/notes")
        val noteId = parser.parsePolishNoteId(notesHtml) ?: throw saveFailed()
        return verifySaved(noteId, username, tags)
    }

    /** 回读编辑页确认目标用户标签已生效，返回最新完整映射。 */
    private suspend fun verifySaved(
        noteId: Long,
        username: String,
        tags: List<String>,
    ): Map<String, List<String>> {
        val editHtml = api.noteEditHtml(noteId).guardedHtml("/notes/edit/$noteId")
        val content = parser.parseNoteEditContent(editHtml) ?: throw saveFailed()
        val saved = PolishMemberTagParser.parse(content)
        val savedTags = saved.entries
            .firstOrNull { it.key.equals(username, ignoreCase = true) }?.value.orEmpty()
        val expected = tags.map(String::trim).filter(String::isNotBlank).distinct()
        if (savedTags != expected) throw saveFailed()
        return saved
    }

    /** 提交后跟随重定向落点应仍在记事本下，登录页/受限页按对应错误抛出。 */
    private fun Response<ResponseBody>.guardedSubmit() {
        val url = raw().request.url
        if (url.scheme != "https" || url.host != "www.v2ex.com") {
            throw saveFailed()
        }
        if (url.encodedPath == "/signin") throw authInvalid()
        if (!url.encodedPath.startsWith("/notes")) throw saveFailed()
        val html = bodyStringOrThrow()
        if (parser.hasSignInEntry(html)) throw authInvalid()
        if (parser.hasAccessChallenge(html)) {
            throw NodeFlowException(
                kind = NodeFlowException.Kind.AccessDenied,
                message = "V2EX 暂时拒绝访问记事本",
            )
        }
    }

    private fun contentBroken() = NodeFlowException(
        kind = NodeFlowException.Kind.Parse,
        message = "Polish 设置内容异常，已中止保存",
    )

    private fun saveFailed() = NodeFlowException(
        kind = NodeFlowException.Kind.Parse,
        message = "标签保存校验失败",
    )

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
