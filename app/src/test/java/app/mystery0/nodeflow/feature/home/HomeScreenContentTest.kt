package app.mystery0.nodeflow.feature.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeScreenContentTest {
    @Test
    fun homeReselectAction_scrollsAndRefreshesWhenListHasItems() {
        val action = homeReselectAction(itemCount = 10)

        assertThat(action.shouldScrollToTop).isTrue()
        assertThat(action.shouldRefresh).isTrue()
    }

    @Test
    fun homeReselectAction_refreshesWithoutScrollingWhenListIsEmpty() {
        val action = homeReselectAction(itemCount = 0)

        assertThat(action.shouldScrollToTop).isFalse()
        assertThat(action.shouldRefresh).isTrue()
    }
}
