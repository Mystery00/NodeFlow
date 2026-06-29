package app.mystery0.nodeflow.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigationRouteTest {
    @Test
    fun isTopLevelRoute_returnsTrueForBottomNavigationRoutes() {
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Home)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeList)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Settings)).isTrue()
    }

    @Test
    fun isTopLevelRoute_returnsFalseForDetailRoutes() {
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.TopicRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.ProfileRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(null)).isFalse()
    }

    @Test
    fun nodeList_usesStableRouteName() {
        assertThat(NodeFlowDestinations.NodeList).isEqualTo("nodes")
    }
}
