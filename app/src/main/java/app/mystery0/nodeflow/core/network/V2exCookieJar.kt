package app.mystery0.nodeflow.core.network

import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class V2exCookieJar(
    private val storage: V2exCookieStorage = InMemoryV2exCookieStorage(),
    private val clock: () -> Long = System::currentTimeMillis,
) : CookieJar {
    private val cookies = storage.load()
        .mapNotNull { storedCookie ->
            val url = storedCookie.url.toHttpUrlOrNull() ?: return@mapNotNull null
            Cookie.parse(url, storedCookie.value)
        }
        .distinctBy { it.identityKey() }
        .toMutableList()

    init {
        pruneExpiredCookies()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            this.cookies.removeAll { stored ->
                stored.identityKey() == cookie.identityKey()
            }
            if (cookie.expiresAt > clock()) {
                this.cookies += cookie
            }
        }
        persistCookies(url)
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        pruneExpiredCookies()
        return cookies.filter { it.matches(url) }
    }

    fun cookieHeader(url: HttpUrl): String =
        loadForRequest(url).joinToString(separator = "; ") { cookie ->
            "${cookie.name}=${cookie.value}"
        }

    @Synchronized
    fun restoreFromCookieHeader(url: HttpUrl, cookieHeader: String) {
        val restoredCookies = cookieHeader.split(";")
            .mapNotNull { rawCookie ->
                val trimmed = rawCookie.trim()
                val separatorIndex = trimmed.indexOf("=")
                if (separatorIndex <= 0) return@mapNotNull null
                val name = trimmed.substring(0, separatorIndex)
                val value = trimmed.substring(separatorIndex + 1)
                runCatching {
                    Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                }.getOrNull()
            }
        if (restoredCookies.isEmpty()) return
        saveFromResponse(url, restoredCookies)
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        storage.clear()
    }

    private fun pruneExpiredCookies() {
        val removed = cookies.removeAll { it.expiresAt <= clock() }
        if (removed) {
            persistCookies(V2EX_HOME_URL)
        }
    }

    private fun persistCookies(url: HttpUrl) {
        storage.save(
            cookies.map { cookie ->
                StoredCookie(
                    url = url.toString(),
                    value = cookie.toString(),
                )
            },
        )
    }

    private fun Cookie.identityKey(): String = "$name|$domain|$path"

    private companion object {
        val V2EX_HOME_URL = "https://www.v2ex.com/".toHttpUrlOrNull()!!
    }
}

interface V2exCookieStorage {
    fun load(): List<StoredCookie>
    fun save(cookies: List<StoredCookie>)
    fun clear()
}

@Serializable
data class StoredCookie(
    val url: String,
    val value: String,
)

internal class SharedPreferencesV2exCookieStorage(
    private val preferences: SharedPreferences,
) : V2exCookieStorage {
    override fun load(): List<StoredCookie> {
        val raw = preferences.getString(COOKIES_KEY, null) ?: return emptyList()
        return runCatching {
            CookieJson.decodeFromString(ListSerializer(StoredCookie.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    override fun save(cookies: List<StoredCookie>) {
        val raw = CookieJson.encodeToString(ListSerializer(StoredCookie.serializer()), cookies)
        preferences.edit().putString(COOKIES_KEY, raw).apply()
    }

    override fun clear() {
        preferences.edit().remove(COOKIES_KEY).apply()
    }

    private companion object {
        const val COOKIES_KEY = "cookies"
    }
}

internal class InMemoryV2exCookieStorage : V2exCookieStorage {
    private var cookies: List<StoredCookie> = emptyList()

    override fun load(): List<StoredCookie> = cookies

    override fun save(cookies: List<StoredCookie>) {
        this.cookies = cookies
    }

    override fun clear() {
        cookies = emptyList()
    }
}

private val CookieJson = Json {
    ignoreUnknownKeys = true
}
