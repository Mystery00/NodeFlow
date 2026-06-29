package app.mystery0.nodeflow.core.designsystem.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NodeChipTest {
    @Test
    fun compactNodeChipLayout_usesReferenceTagDensity() {
        val layout = compactNodeChipLayout()

        assertThat(layout.height.value).isEqualTo(24f)
        assertThat(layout.horizontalPadding.value).isEqualTo(8f)
    }
}
