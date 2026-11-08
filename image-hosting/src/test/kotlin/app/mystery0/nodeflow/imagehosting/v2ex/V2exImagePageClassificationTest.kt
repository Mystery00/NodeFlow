package app.mystery0.nodeflow.imagehosting.v2ex

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class V2exImagePageClassificationTest {
    private val parser = V2exImageParser()

    @Test fun uploadFormWithLoginLinkAndChallengeScriptIsAvailable() {
        val html = """<a href='/signin'>登录</a><script src='/challenge.js'></script>
            <form action='/i/upload'><input type='file' name='qqfile'></form>"""
        assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", html))
            .isEqualTo(V2exUploadPageAccess.Available)
    }

    @Test fun imageIdContainingChallengeIsNormalResponse() {
        assertThat(parser.classifyUploadResponse("https://www.v2ex.com/i/upload",
            """{"success":true,"name":"challenge-image"}"""))
            .isEqualTo(V2exUploadResponseAccess.Normal)
    }

    @Test fun realSignInFormAndChallengeAreRecognized() {
        assertThat(parser.classifyUploadPage("https://www.v2ex.com/i/upload", "<form action='/signin'></form>"))
            .isEqualTo(V2exUploadPageAccess.AuthenticationRequired)
        assertThat(parser.classifyUploadResponse("https://www.v2ex.com/i/upload", "<form id='challenge-form'></form>"))
            .isEqualTo(V2exUploadResponseAccess.Challenge)
    }
}
