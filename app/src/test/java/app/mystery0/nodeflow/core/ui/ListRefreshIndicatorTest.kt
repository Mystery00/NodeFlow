package app.mystery0.nodeflow.core.ui

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ListRefreshIndicatorTest {
    @Test
    fun shouldShowListRefreshIndicator_showsOnlyWhenRefreshingWithItems() {
        assertThat(shouldShowListRefreshIndicator(isRefreshing = true, itemCount = 1)).isTrue()
    }

    @Test
    fun shouldShowListRefreshIndicator_hidesWhenRefreshingWithoutItems() {
        assertThat(shouldShowListRefreshIndicator(isRefreshing = true, itemCount = 0)).isFalse()
    }

    @Test
    fun shouldShowListRefreshIndicator_hidesWhenItemsExistButNotRefreshing() {
        assertThat(shouldShowListRefreshIndicator(isRefreshing = false, itemCount = 1)).isFalse()
    }
}
