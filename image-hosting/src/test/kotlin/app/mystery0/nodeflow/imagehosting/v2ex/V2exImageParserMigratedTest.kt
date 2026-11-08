package app.mystery0.nodeflow.imagehosting.v2ex

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** 从已删除的 app 兼容上传器迁移，直接覆盖当前图床解析器。 */
class V2exImageParserMigratedTest {
    private val parser = V2exImageParser()
    private fun parseResponse(body: String) = parser.parse(body, "application/json")

    @Test
    fun parseImageUploadResponse_acceptsStringSuccessAndNormalizesUrl() {
        val parsed = parseResponse(
            """{"success":"true","name":"sample","uri":"sample.png","url_o":"//i.v2ex.co/sample.png","url_b":"//i.v2ex.co/sampleb.png"}""",
        )

        assertThat(parsed?.imageId).isEqualTo("sample")
        assertThat(parsed?.originalUrl).isEqualTo("https://i.v2ex.co/sample.png")
        assertThat(parsed?.detailUrl).isEqualTo("https://www.v2ex.com/i/sample.png")
    }

    @Test
    fun parseImageUploadResponse_rejectsFailureAndUnexpectedHost() {
        assertThat(parseResponse("""{"success":"false","message":"quota"}"""))
            .isNull()
        assertThat(
            parseResponse(
                """{"success":true,"name":"sample","uri":"sample.png","url_o":"//example.com/sample.png"}""",
            ),
        ).isNull()
    }

    @Test
    fun parseImageUploadPage_acceptsFineUploaderBeforeJavaScriptRuns() {
        val html = """
            <div id="uploader"></div>
            <script>
                var uploader = new qq.FineUploader({
                    element: document.getElementById("uploader"),
                    request: { endpoint: '/i/upload' }
                });
            </script>
        """.trimIndent()

        assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", html))
            .isEqualTo(V2exUploadPageAccess.Available)
    }

    @Test
    fun parseImageUploadPage_rejectsIncompleteOrUntrustedUploader() {
        val initializer = """
            <script>new qq.FineUploader({
                element: document.getElementById('uploader'),
                request: { endpoint: '/i/upload' }
            });</script>
        """.trimIndent()
        val available = "<div id='uploader'></div>$initializer"
        listOf(
            initializer,
            "<div id='uploader'></div>",
            available.replace("'/i/upload'", "'https://example.com/i/upload'"),
            available.replace("getElementById('uploader')", "getElementById('other')"),
        ).forEach { html ->
            assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", html))
                .isEqualTo(V2exUploadPageAccess.Unrecognized)
        }
        assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", "<title>Just a moment...</title>$available"))
            .isEqualTo(V2exUploadPageAccess.Challenge)
        assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", "<form action='/signin'><input type='password'></form>$available"))
            .isEqualTo(V2exUploadPageAccess.AuthenticationRequired)
    }

    @Test
    fun parseImageUploadPage_classifiesAvailableLoginAndUnknownPages() {
        assertThat(
            parser.classifyUploadPage("https://www.v2ex.com/i/upload",
                """<form action="/i/upload" method="post"><input type="file" name="qqfile" /></form>""",
            ),
        ).isEqualTo(V2exUploadPageAccess.Available)
        assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", """<form action="/signin"></form>"""))
            .isEqualTo(V2exUploadPageAccess.AuthenticationRequired)
        assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", """<a href="/i/about">图库介绍</a>"""))
            .isEqualTo(V2exUploadPageAccess.Unrecognized)
    }
}
