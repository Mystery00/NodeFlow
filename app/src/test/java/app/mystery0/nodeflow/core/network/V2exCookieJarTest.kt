package app.mystery0.nodeflow.core.network

import com.google.common.truth.Truth.assertThat
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class V2exCookieJarTest {
    @Test
    fun cookieHeader_returnsCookiesMatchingCurrentDomain() {
        val cookieJar = V2exCookieJar()
        val v2exUrl = "https://www.v2ex.com/signin".toHttpUrl()
        val otherUrl = "https://example.com/".toHttpUrl()
        val authCookie = Cookie.Builder()
            .domain("www.v2ex.com")
            .path("/")
            .name("A2")
            .value("auth")
            .build()
        val languageCookie = Cookie.Builder()
            .domain("www.v2ex.com")
            .path("/")
            .name("V2EX_LANG")
            .value("zhcn")
            .build()

        cookieJar.saveFromResponse(v2exUrl, listOf(authCookie, languageCookie))

        assertThat(cookieJar.cookieHeader(v2exUrl)).isEqualTo("A2=auth; V2EX_LANG=zhcn")
        assertThat(cookieJar.cookieHeader(otherUrl)).isEmpty()
    }
}
