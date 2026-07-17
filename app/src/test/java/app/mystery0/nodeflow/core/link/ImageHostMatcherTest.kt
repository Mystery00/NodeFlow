package app.mystery0.nodeflow.core.link

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ImageHostMatcherTest {
    @Test
    fun normalizeHost_stripsSchemePathPortAndLowercases() {
        assertThat(ImageHostMatcher.normalizeHost("https://Img.Example.com/path?q=1#f"))
            .isEqualTo("img.example.com")
        assertThat(ImageHostMatcher.normalizeHost("http://example.com:8080/"))
            .isEqualTo("example.com")
        assertThat(ImageHostMatcher.normalizeHost("  example.com  ")).isEqualTo("example.com")
        assertThat(ImageHostMatcher.normalizeHost("localhost")).isEqualTo("localhost")
    }

    @Test
    fun normalizeHost_rejectsInvalidInput() {
        assertThat(ImageHostMatcher.normalizeHost("")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("   ")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("https://")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("exa mple.com")).isNull()
        assertThat(ImageHostMatcher.normalizeHost("例子.com")).isNull()
        assertThat(ImageHostMatcher.normalizeHost(".example.com")).isNull()
    }

    @Test
    fun shouldLoadAsImage_matchesHostAndSubdomains() {
        val hosts = setOf("example.com")
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com/abc", hosts)).isTrue()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://img.example.com/abc", hosts)).isTrue()
        assertThat(ImageHostMatcher.shouldLoadAsImage("HTTPS://EXAMPLE.COM/ABC", hosts)).isTrue()
    }

    @Test
    fun shouldLoadAsImage_configuredSubdomainDoesNotMatchParent() {
        val hosts = setOf("img.example.com")
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://img.example.com/a", hosts)).isTrue()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com/a", hosts)).isFalse()
    }

    @Test
    fun shouldLoadAsImage_rejectsNonHttpAndLookalikes() {
        val hosts = setOf("example.com")
        assertThat(ImageHostMatcher.shouldLoadAsImage("ftp://example.com/a", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://fake-example.com/a", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com.evil.com/a", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("not a url", hosts)).isFalse()
        assertThat(ImageHostMatcher.shouldLoadAsImage("https://example.com/a", emptySet())).isFalse()
    }
}
