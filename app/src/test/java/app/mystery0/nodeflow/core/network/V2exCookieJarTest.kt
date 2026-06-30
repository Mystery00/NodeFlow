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

    @Test
    fun loadForRequest_restoresCookiesFromStorage() {
        val storage = FakeCookieStorage()
        val v2exUrl = "https://www.v2ex.com/signin".toHttpUrl()
        val firstJar = V2exCookieJar(storage = storage)
        firstJar.saveFromResponse(
            v2exUrl,
            listOf(
                Cookie.Builder()
                    .domain("www.v2ex.com")
                    .path("/")
                    .name("A2")
                    .value("auth")
                    .build(),
            ),
        )

        val restoredJar = V2exCookieJar(storage = storage)

        assertThat(restoredJar.cookieHeader(v2exUrl)).isEqualTo("A2=auth")
    }

    @Test
    fun saveFromResponse_removesExpiredCookiesFromStorage() {
        val storage = FakeCookieStorage()
        val v2exUrl = "https://www.v2ex.com/signin".toHttpUrl()
        val cookieJar = V2exCookieJar(storage = storage)
        cookieJar.saveFromResponse(
            v2exUrl,
            listOf(
                Cookie.Builder()
                    .domain("www.v2ex.com")
                    .path("/")
                    .name("A2")
                    .value("auth")
                    .build(),
            ),
        )

        val expiredCookie = Cookie.parse(
            v2exUrl,
            "A2=deleted; Max-Age=0; Domain=www.v2ex.com; Path=/",
        )!!
        cookieJar.saveFromResponse(v2exUrl, listOf(expiredCookie))
        val restoredJar = V2exCookieJar(storage = storage)

        assertThat(restoredJar.cookieHeader(v2exUrl)).isEmpty()
        assertThat(storage.cookies).isEmpty()
    }

    @Test
    fun clear_removesMemoryAndStoredCookies() {
        val storage = FakeCookieStorage()
        val v2exUrl = "https://www.v2ex.com/signin".toHttpUrl()
        val cookieJar = V2exCookieJar(storage = storage)
        cookieJar.saveFromResponse(
            v2exUrl,
            listOf(
                Cookie.Builder()
                    .domain("www.v2ex.com")
                    .path("/")
                    .name("A2")
                    .value("auth")
                    .build(),
            ),
        )

        cookieJar.clear()
        val restoredJar = V2exCookieJar(storage = storage)

        assertThat(cookieJar.cookieHeader(v2exUrl)).isEmpty()
        assertThat(restoredJar.cookieHeader(v2exUrl)).isEmpty()
        assertThat(storage.cookies).isEmpty()
    }

    @Test
    fun restoreFromCookieHeader_persistsCookiesForLaterRequests() {
        val storage = FakeCookieStorage()
        val v2exUrl = "https://www.v2ex.com/signin".toHttpUrl()
        val cookieJar = V2exCookieJar(storage = storage)

        cookieJar.restoreFromCookieHeader(v2exUrl, "A2=auth; V2EX_LANG=zhcn")
        val restoredJar = V2exCookieJar(storage = storage)

        assertThat(restoredJar.cookieHeader(v2exUrl)).isEqualTo("A2=auth; V2EX_LANG=zhcn")
    }

    private class FakeCookieStorage : V2exCookieStorage {
        var cookies: List<StoredCookie> = emptyList()
            private set

        override fun load(): List<StoredCookie> = cookies

        override fun save(cookies: List<StoredCookie>) {
            this.cookies = cookies
        }

        override fun clear() {
            cookies = emptyList()
        }
    }
}
