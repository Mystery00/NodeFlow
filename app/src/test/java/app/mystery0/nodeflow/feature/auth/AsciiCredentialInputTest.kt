package app.mystery0.nodeflow.feature.auth

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AsciiCredentialInputTest {
    @Test
    fun keepsAsciiLettersDigitsAndSymbols() {
        assertThat(asciiCredentialInput("User_01@v2ex.com")).isEqualTo("User_01@v2ex.com")
        assertThat(asciiCredentialInput("P@ss-word!123")).isEqualTo("P@ss-word!123")
    }

    @Test
    fun stripsNonAsciiCharacters() {
        assertThat(asciiCredentialInput("用户abc名")).isEqualTo("abc")
        assertThat(asciiCredentialInput("ｆｕｌｌ宽width")).isEqualTo("width")
        assertThat(asciiCredentialInput("emoji😀ok")).isEqualTo("emojiok")
    }

    @Test
    fun stripsWhitespaceAndControlCharacters() {
        assertThat(asciiCredentialInput("a b\tc\nd")).isEqualTo("abcd")
    }
}
