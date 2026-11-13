package app.mystery0.nodeflow.data.membertag

import app.mystery0.nodeflow.core.common.NodeFlowException
import app.mystery0.nodeflow.core.network.V2exRawApi
import app.mystery0.nodeflow.core.parser.V2exHtmlParser
import app.mystery0.nodeflow.testing.TestV2exRawApi
import app.mystery0.nodeflow.testing.emptyHtmlResponse
import app.mystery0.nodeflow.testing.testHtmlResponse
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody
import org.junit.Test
import retrofit2.Response

class MemberTagRemoteDataSourceTest {
    private fun dataSource(api: V2exRawApi) = MemberTagRemoteDataSource(api, V2exHtmlParser())

    @Test
    fun updateMemberTags_patchesExistingNotePreservingOtherSettings() = runTest {
        val api = FakeNotesApi(
            noteContent = "V2EX_Polish_settings" +
                """{"settings-sync":{"version":46},"member-tag":{"Alice":{"tags":["大佬"]}}}""",
        )

        val tags = dataSource(api).updateMemberTags(
            "bob", listOf("后端"), "https://cdn.v2ex.com/b.png",
        )

        assertThat(tags).containsExactly(
            "Alice", listOf("大佬"),
            "bob", listOf("后端"),
        )
        assertThat(api.editSubmitCount).isEqualTo(1)
        assertThat(api.newSubmitCount).isEqualTo(0)
        // 写回时同步版本号 +1，插件端才会拉取本次修改
        assertThat(api.noteContent).contains("\"version\":47")
        assertThat(api.noteContent).contains("https://cdn.v2ex.com/b.png")
    }

    @Test
    fun updateMemberTags_abortsWithoutSubmittingWhenContentBroken() = runTest {
        val api = FakeNotesApi(noteContent = "V2EX_Polish_settings not json")

        val error = runCatching {
            dataSource(api).updateMemberTags("Alice", listOf("大佬"), null)
        }.exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
        assertThat(api.editSubmitCount).isEqualTo(0)
        assertThat(api.newSubmitCount).isEqualTo(0)
        assertThat(api.noteContent).isEqualTo("V2EX_Polish_settings not json")
    }

    @Test
    fun updateMemberTags_createsNoteWhenMissing() = runTest {
        val api = FakeNotesApi(noteContent = null)

        val tags = dataSource(api).updateMemberTags(
            "Alice", listOf("大佬"), "https://cdn.v2ex.com/a.png",
        )

        assertThat(tags).containsExactly("Alice", listOf("大佬"))
        assertThat(api.newSubmitCount).isEqualTo(1)
        assertThat(api.editSubmitCount).isEqualTo(0)
        assertThat(api.noteContent).startsWith("V2EX_Polish_settings")
    }

    @Test
    fun updateMemberTags_isNoopForEmptyTagsWithoutNote() = runTest {
        val api = FakeNotesApi(noteContent = null)

        val tags = dataSource(api).updateMemberTags("Alice", emptyList(), null)

        assertThat(tags).isEmpty()
        assertThat(api.newSubmitCount).isEqualTo(0)
        assertThat(api.editSubmitCount).isEqualTo(0)
    }

    @Test
    fun updateMemberTags_removesEntryWhenTagsCleared() = runTest {
        val api = FakeNotesApi(
            noteContent = "V2EX_Polish_settings" +
                """{"member-tag":{"Alice":{"tags":["大佬"]},"bob":{"tags":["后端"]}}}""",
        )

        val tags = dataSource(api).updateMemberTags("Alice", emptyList(), null)

        assertThat(tags).containsExactly("bob", listOf("后端"))
        assertThat(api.noteContent).doesNotContain("Alice")
    }

    @Test
    fun updateMemberTags_reportsAuthWhenRedirectedToSignIn() = runTest {
        val api = FakeNotesApi(noteContent = null, signInRedirect = true)

        val error = runCatching {
            dataSource(api).updateMemberTags("Alice", listOf("大佬"), null)
        }.exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Auth)
    }

    @Test
    fun updateMemberTags_failsWhenServerDidNotPersist() = runTest {
        val api = FakeNotesApi(
            noteContent = "V2EX_Polish_settings" + """{"member-tag":{}}""",
            ignoreSubmits = true,
        )

        val error = runCatching {
            dataSource(api).updateMemberTags("Alice", listOf("大佬"), null)
        }.exceptionOrNull() as NodeFlowException

        assertThat(error.kind).isEqualTo(NodeFlowException.Kind.Parse)
    }

    /** 模拟记事本服务端：单条 Polish 记事，提交即落库（可配置为不落库）。 */
    private class FakeNotesApi(
        var noteContent: String?,
        private val ignoreSubmits: Boolean = false,
        private val signInRedirect: Boolean = false,
    ) : TestV2exRawApi(fallback = { emptyHtmlResponse() }) {
        var editSubmitCount = 0
            private set
        var newSubmitCount = 0
            private set
        private val noteId = 42L

        override suspend fun notesHtml(): Response<ResponseBody> {
            if (signInRedirect) {
                return testHtmlResponse(
                    "<form action='/signin'><input name='once'></form>",
                    "https://www.v2ex.com/signin?next=/notes",
                )
            }
            val link = noteContent
                ?.let { "<a href=\"/notes/$noteId\">${it.take(40)}</a>" }
                .orEmpty()
            return testHtmlResponse("<html><body>$link</body></html>", "https://www.v2ex.com/notes")
        }

        override suspend fun noteEditHtml(id: Long): Response<ResponseBody> = testHtmlResponse(
            "<html><body><textarea name=\"content\">${noteContent.orEmpty()}</textarea></body></html>",
            "https://www.v2ex.com/notes/edit/$id",
        )

        override suspend fun noteEditSubmit(id: Long, content: String, syntax: String): Response<ResponseBody> {
            editSubmitCount++
            if (!ignoreSubmits) noteContent = content
            return testHtmlResponse("<html><body></body></html>", "https://www.v2ex.com/notes/$id")
        }

        override suspend fun noteNewSubmit(content: String, syntax: String): Response<ResponseBody> {
            newSubmitCount++
            if (!ignoreSubmits) noteContent = content
            return testHtmlResponse("<html><body></body></html>", "https://www.v2ex.com/notes/$noteId")
        }
    }
}
