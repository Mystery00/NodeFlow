package app.mystery0.nodeflow.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RootScaffoldPaddingTest {
    @Test
    fun rootContentPadding_keepsOnlyBottomPaddingForNavHost() {
        val scaffoldPadding = PaddingValues(
            top = 24.dp,
            bottom = 80.dp,
        )

        val navHostPadding = rootNavHostPadding(scaffoldPadding)

        assertThat(navHostPadding.calculateTopPadding().value).isEqualTo(0f)
        assertThat(navHostPadding.calculateBottomPadding().value).isEqualTo(80f)
    }
}
