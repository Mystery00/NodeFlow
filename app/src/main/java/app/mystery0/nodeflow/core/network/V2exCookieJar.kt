package app.mystery0.nodeflow.core.network

import app.mystery0.nodeflow.core.security.EncryptedKeyValueStore
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
    private val cookies = mutableListOf<Cookie>()
    private val cookieOrigins = mutableMapOf<String, HttpUrl>()

    init {
        storage.load().forEach { storedCookie ->
            val cookie = storedCookie.toCookie() ?: return@forEach
            val origin = storedCookie.url.toHttpUrlOrNull() ?: return@forEach
            val identity = cookie.identityKey()
            if (cookieOrigins.putIfAbsent(identity, origin) == null) {
                cookies += cookie
            }
        }
        pruneExpiredCookies()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            val identity = cookie.identityKey()
            this.cookies.removeAll { stored ->
                stored.identityKey() == identity
            }
            cookieOrigins.remove(identity)
            if (cookie.expiresAt > clock()) {
                this.cookies += cookie
                cookieOrigins[identity] = url
            }
        }
        persistCookies()
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
                try {
                    Cookie.Builder()
                        .domain(url.host)
                        .path("/")
                        .name(name)
                        .value(value)
                        .build()
                } catch (_: Exception) {
                    null
                }
            }
        if (restoredCookies.isEmpty()) return
        saveFromResponse(url, restoredCookies)
    }

    @Synchronized
    fun clear() {
        cookies.clear()
        cookieOrigins.clear()
        storage.clear()
    }

    private fun pruneExpiredCookies() {
        val expired = cookies.filter { it.expiresAt <= clock() }
        if (expired.isNotEmpty()) {
            cookies.removeAll(expired.toSet())
            expired.forEach { cookieOrigins.remove(it.identityKey()) }
            persistCookies()
        }
    }

    private fun persistCookies() {
        storage.save(
            cookies.map { cookie ->
                StoredCookie(
                    url = (cookieOrigins[cookie.identityKey()] ?: V2EX_HOME_URL).toString(),
                    value = cookie.toString(),
                    name = cookie.name,
                    cookieValue = cookie.value,
                    domain = cookie.domain,
                    path = cookie.path,
                    expiresAt = cookie.expiresAt,
                    persistent = cookie.persistent,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly,
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
    val name: String? = null,
    val cookieValue: String? = null,
    val domain: String? = null,
    val path: String? = null,
    val expiresAt: Long? = null,
    val persistent: Boolean? = null,
    val secure: Boolean? = null,
    val httpOnly: Boolean? = null,
    val hostOnly: Boolean? = null,
)

private fun StoredCookie.toCookie(): Cookie? {
    val origin = url.toHttpUrlOrNull() ?: return null
    val cookieName = name ?: return Cookie.parse(origin, value)
    val cookieValue = cookieValue ?: return Cookie.parse(origin, value)
    val cookieDomain = domain ?: return Cookie.parse(origin, value)
    val cookiePath = path ?: return Cookie.parse(origin, value)
    return try {
        Cookie.Builder()
            .name(cookieName)
            .value(cookieValue)
            .apply {
                if (hostOnly == true) hostOnlyDomain(cookieDomain) else domain(cookieDomain)
            }
            .path(cookiePath)
            .apply {
                if (persistent == true) {
                    expiresAt?.let { expiry -> expiresAt(expiry) }
                }
                if (secure == true) secure()
                if (httpOnly == true) httpOnly()
            }
            .build()
    } catch (_: Exception) {
        null
    }
}

internal class EncryptedV2exCookieStorage(
    private val storage: EncryptedKeyValueStore,
) : V2exCookieStorage {
    override fun load(): List<StoredCookie> {
        val raw = storage.read(COOKIES_KEY) ?: return emptyList()
        return try {
            CookieJson.decodeFromString(ListSerializer(StoredCookie.serializer()), raw)
        } catch (_: Exception) {
            storage.remove(COOKIES_KEY)
            emptyList()
        }
    }

    override fun save(cookies: List<StoredCookie>) {
        val raw = CookieJson.encodeToString(ListSerializer(StoredCookie.serializer()), cookies)
        storage.write(COOKIES_KEY, raw)
    }

    override fun clear() {
        storage.remove(COOKIES_KEY)
    }

    private companion object {
        const val COOKIES_KEY = "v2ex_cookies"
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
