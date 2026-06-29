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

    @Test
    fun rootStartDestination_usesMainShellRoute() {
        assertThat(rootStartDestination()).isEqualTo(NodeFlowDestinations.Main)
    }

    @Test
    fun isRootDetailRoute_matchesOnlyRootLevelDetailRoutes() {
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.TopicRoute)).isTrue()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.NodeRoute)).isTrue()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.ProfileRoute)).isTrue()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Home)).isFalse()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.NodeList)).isFalse()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Settings)).isFalse()
        assertThat(NodeFlowDestinations.isRootDetailRoute(null)).isFalse()
    }

    @Test
    fun nodeBottomBarRoute_targetsNodeListInsteadOfDefaultNodeDetail() {
        assertThat(nodeBottomBarRoute()).isEqualTo(NodeFlowDestinations.NodeList)
    }

    @Test
    fun isNodeBottomBarSelected_matchesOnlyNodeListRoute() {
        assertThat(isNodeBottomBarSelected(NodeFlowDestinations.NodeList)).isTrue()
        assertThat(isNodeBottomBarSelected(NodeFlowDestinations.NodeRoute)).isFalse()
        assertThat(isNodeBottomBarSelected("node/python")).isFalse()
    }
}
