package app.mystery0.nodeflow.imagehosting.imgur

import com.google.common.truth.Truth.assertThat
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Test

class ImgurCookieJarTest {
    @Test fun domainCookieReachesSubdomainButHostOnlyAndSecureCookiesRemainScoped() {
        val jar = ImgurCookieJar()
        val page = "https://imgur.com/upload".toHttpUrl()
        jar.saveFromResponse(page, listOf(
            Cookie.parse(page, "shared=one; Domain=imgur.com; Path=/")!!,
            Cookie.parse(page, "local=two; Path=/")!!,
            Cookie.parse(page, "secure=three; Domain=imgur.com; Secure; Path=/")!!,
        ))
        assertThat(jar.loadForRequest("https://api.imgur.com/3/upload".toHttpUrl()).map { it.name })
            .containsExactly("shared", "secure")
        assertThat(jar.loadForRequest("http://api.imgur.com/3/upload".toHttpUrl()).map { it.name })
            .containsExactly("shared")
        assertThat(jar.loadForRequest("https://other.test/".toHttpUrl())).isEmpty()
    }

    @Test fun expiredReplacementDeletesOnlyMatchingCookieAndPreservesOtherPaths() {
        val jar = ImgurCookieJar()
        val page = "https://imgur.com/upload".toHttpUrl()
        jar.saveFromResponse(page, listOf(
            Cookie.parse(page, "session=old; Path=/")!!,
            Cookie.parse(page, "session=page; Path=/upload")!!,
            Cookie.Builder().name("expired").value("value").domain("imgur.com").expiresAt(1).build(),
        ))
        jar.saveFromResponse(page, listOf(Cookie.parse(page, "session=gone; Path=/; Max-Age=0")!!))
        assertThat(jar.loadForRequest(page).map { it.value }).containsExactly("page")
        assertThat(jar.loadForRequest("https://imgur.com/3/upload".toHttpUrl())).isEmpty()
    }
}
