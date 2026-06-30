package app.mystery0.nodeflow.core.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class V2exCookieJar : CookieJar {
    private val cookies = mutableListOf<Cookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { cookie ->
            this.cookies.removeAll { stored ->
                stored.name == cookie.name &&
                    stored.domain == cookie.domain &&
                    stored.path == cookie.path
            }
            if (cookie.expiresAt > now) {
                this.cookies += cookie
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        cookies.removeAll { it.expiresAt <= now }
        return cookies.filter { it.matches(url) }
    }

    fun cookieHeader(url: HttpUrl): String =
        loadForRequest(url).joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }

    @Synchronized
    fun clear() {
        cookies.clear()
    }
}
