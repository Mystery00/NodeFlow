package app.mystery0.nodeflow.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CustomImageHostsLogicTest {
    @Test
    fun add_normalizesAndAppends() {
        val result = addCustomImageHost(listOf("a.com"), "https://Img.Example.com/x")
        assertThat(result).isEqualTo(
            AddImageHostResult.Added(listOf("a.com", "img.example.com")),
        )
    }

    @Test
    fun add_rejectsInvalidInput() {
        assertThat(addCustomImageHost(emptyList(), "   ")).isEqualTo(AddImageHostResult.Invalid)
        assertThat(addCustomImageHost(emptyList(), "bad host")).isEqualTo(AddImageHostResult.Invalid)
    }

    @Test
    fun add_rejectsDuplicateAfterNormalization() {
        assertThat(addCustomImageHost(listOf("example.com"), "HTTPS://EXAMPLE.COM/"))
            .isEqualTo(AddImageHostResult.Duplicate)
    }

    @Test
    fun add_rejectsBuiltInHosts() {
        assertThat(addCustomImageHost(emptyList(), "i.imgur.com"))
            .isEqualTo(AddImageHostResult.Duplicate)
        assertThat(addCustomImageHost(emptyList(), "https://i.v2ex.co/"))
            .isEqualTo(AddImageHostResult.Duplicate)
    }
}
