package app.mystery0.nodeflow.feature.node

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodeListContentTest {
    @Test
    fun defaultNodeListChips_containsStableStarterNodes() {
        val chips = defaultNodeListChips()

        assertThat(chips.map { it.name }).containsAtLeast("python", "android", "programmer", "create")
    }

    @Test
    fun defaultNodeListChips_hasReadableTitles() {
        val chips = defaultNodeListChips()

        assertThat(chips.first { it.name == "android" }.title).isEqualTo("Android")
        assertThat(chips.first { it.name == "python" }.title).isEqualTo("Python")
    }
}
