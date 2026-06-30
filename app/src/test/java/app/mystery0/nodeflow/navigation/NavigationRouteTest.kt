package app.mystery0.nodeflow.navigation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NavigationRouteTest {
    @Test
    fun isTopLevelRoute_returnsTrueForBottomNavigationRoutes() {
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Home)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeList)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Account)).isTrue()
    }

    @Test
    fun isTopLevelRoute_returnsFalseForDetailRoutes() {
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.TopicRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.ProfileRoute)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Settings)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Auth)).isFalse()
        assertThat(NodeFlowDestinations.isTopLevelRoute(null)).isFalse()
    }

    @Test
    fun nodeList_usesStableRouteName() {
        assertThat(NodeFlowDestinations.NodeList).isEqualTo("nodes")
    }

    @Test
    fun account_usesStableRouteName() {
        assertThat(NodeFlowDestinations.Account).isEqualTo("account")
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
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Settings)).isTrue()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Auth)).isTrue()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Home)).isFalse()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.NodeList)).isFalse()
        assertThat(NodeFlowDestinations.isRootDetailRoute(NodeFlowDestinations.Account)).isFalse()
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

    @Test
    fun accountBottomBarRoute_targetsCurrentAccountPage() {
        assertThat(accountBottomBarRoute()).isEqualTo(NodeFlowDestinations.Account)
    }

    @Test
    fun isAccountBottomBarSelected_matchesOnlyAccountRoute() {
        assertThat(isAccountBottomBarSelected(NodeFlowDestinations.Account)).isTrue()
        assertThat(isAccountBottomBarSelected(NodeFlowDestinations.Settings)).isFalse()
        assertThat(isAccountBottomBarSelected(NodeFlowDestinations.Auth)).isFalse()
    }
}
