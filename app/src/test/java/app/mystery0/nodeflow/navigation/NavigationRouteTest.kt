package app.mystery0.nodeflow.navigation

import app.mystery0.nodeflow.core.link.V2exLink
import app.mystery0.nodeflow.core.model.AccountOverview
import app.mystery0.nodeflow.core.model.AuthSession
import app.mystery0.nodeflow.core.model.DailyCheckIn
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.feature.account.AccountUiState
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class NavigationRouteTest {
    @Test
    fun isTopLevelRoute_returnsTrueForBottomNavigationRoutes() {
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Home)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.NodeList)).isTrue()
        assertThat(NodeFlowDestinations.isTopLevelRoute(NodeFlowDestinations.Notification)).isTrue()
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
    fun routeFor_mapsTopicLinkToTopicRoute() {
        // node/member 路由包含 Uri.encode，依赖 Android 运行时，这里只验证 topic 映射
        assertThat(NodeFlowDestinations.routeFor(V2exLink.Topic(1226527)))
            .isEqualTo("topic/1226527")
    }

    @Test
    fun topic_includesReplyFloorWhenProvided() {
        assertThat(NodeFlowDestinations.topic(topicId = 1226527, replyFloor = 18))
            .isEqualTo("topic/1226527?replyFloor=18")
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

    @Test
    fun messageBottomBarRoute_targetsNotificationPage() {
        assertThat(messageBottomBarRoute()).isEqualTo(NodeFlowDestinations.Notification)
    }

    @Test
    fun isMessageBottomBarSelected_matchesOnlyNotificationRoute() {
        assertThat(isMessageBottomBarSelected(NodeFlowDestinations.Notification)).isTrue()
        assertThat(isMessageBottomBarSelected(NodeFlowDestinations.Account)).isFalse()
        assertThat(isMessageBottomBarSelected(NodeFlowDestinations.TopicRoute)).isFalse()
    }

    @Test
    fun messageBottomBarBadgeText_returnsNullWhenLoggedOut() {
        val state = AccountUiState(
            overview = AccountOverview(unreadNotificationCount = 8),
        )

        assertThat(messageBottomBarBadgeText(state)).isNull()
    }

    @Test
    fun messageBottomBarBadgeText_returnsNullWhileLoadingUnknownCount() {
        val state = AccountUiState(
            session = loggedInSession(),
            isLoading = true,
        )

        assertThat(messageBottomBarBadgeText(state)).isNull()
    }

    @Test
    fun messageBottomBarBadgeText_returnsBangWhenLoadedCountIsUnavailable() {
        val state = AccountUiState(session = loggedInSession())

        assertThat(messageBottomBarBadgeText(state)).isEqualTo("!")
    }

    @Test
    fun messageBottomBarBadgeText_returnsNullForZeroUnreadCount() {
        val state = AccountUiState(
            session = loggedInSession(),
            overview = AccountOverview(unreadNotificationCount = 0),
        )

        assertThat(messageBottomBarBadgeText(state)).isNull()
    }

    @Test
    fun messageBottomBarBadgeText_returnsUnreadCount() {
        val state = AccountUiState(
            session = loggedInSession(),
            overview = AccountOverview(unreadNotificationCount = 8),
        )

        assertThat(messageBottomBarBadgeText(state)).isEqualTo("8")
    }

    @Test
    fun messageBottomBarBadgeText_capsLargeUnreadCount() {
        val state = AccountUiState(
            session = loggedInSession(),
            overview = AccountOverview(unreadNotificationCount = 100),
        )

        assertThat(messageBottomBarBadgeText(state)).isEqualTo("99+")
    }

    @Test
    fun shouldLoadNotificationList_matchesLoginState() {
        assertThat(shouldLoadNotificationList(AccountUiState())).isFalse()
        assertThat(shouldLoadNotificationList(AccountUiState(session = loggedInSession()))).isTrue()
    }

    @Test
    fun shouldShowAccountCheckInBadge_returnsTrueWhenLoggedInAndCanCheckIn() {
        val state = AccountUiState(
            session = loggedInSession(),
            overview = AccountOverview(
                checkIn = DailyCheckIn(checkedIn = false, canCheckIn = true),
            ),
        )

        assertThat(shouldShowAccountCheckInBadge(state)).isTrue()
    }

    @Test
    fun shouldShowAccountCheckInBadge_returnsFalseWhenAlreadyCheckedIn() {
        val state = AccountUiState(
            session = loggedInSession(),
            overview = AccountOverview(
                checkIn = DailyCheckIn(checkedIn = true, canCheckIn = false),
            ),
        )

        assertThat(shouldShowAccountCheckInBadge(state)).isFalse()
    }

    @Test
    fun shouldShowAccountCheckInBadge_returnsFalseWhenLoggedOut() {
        val state = AccountUiState(
            overview = AccountOverview(
                checkIn = DailyCheckIn(checkedIn = false, canCheckIn = true),
            ),
        )

        assertThat(shouldShowAccountCheckInBadge(state)).isFalse()
    }

    @Test
    fun shouldShowAccountCheckInBadge_returnsFalseWhenCheckInStateIsUnknown() {
        val state = AccountUiState(session = loggedInSession())

        assertThat(shouldShowAccountCheckInBadge(state)).isFalse()
    }

    @Test
    fun homeBottomBarLabel_usesPinnedNodeTitleWhenAvailable() {
        val pinned = PinnedHomeNode(
            name = "android",
            title = "Android",
            avatarUrl = "https://cdn.v2ex.com/navatar/android_large.png",
        )

        assertThat(homeBottomBarLabel(pinned)).isEqualTo("Android")
    }

    @Test
    fun homeBottomBarLabel_usesDefaultTextWhenNoPinnedNode() {
        assertThat(homeBottomBarLabel(null)).isEqualTo("首页")
    }

    @Test
    fun homeBottomBarAction_reselectsHomeWhenAlreadyOnHome() {
        assertThat(homeBottomBarAction(NodeFlowDestinations.Home))
            .isEqualTo(HomeBottomBarAction.ReselectHome)
    }

    @Test
    fun homeBottomBarAction_navigatesHomeFromOtherTopLevelRoute() {
        assertThat(homeBottomBarAction(NodeFlowDestinations.NodeList))
            .isEqualTo(HomeBottomBarAction.NavigateHome)
    }

    @Test
    fun homeReselectRequests_preservesEveryRequestUntilConsumed() = runTest {
        val requests = HomeReselectRequests()

        requests.request()
        requests.request()

        assertThat(requests.events.take(2).toList()).hasSize(2)
    }

    @Test
    fun homeReselectRequests_doesNotReplayConsumedRequest() = runTest {
        val requests = HomeReselectRequests()
        requests.request()
        requests.events.first()

        val replayed = withTimeoutOrNull(1) {
            requests.events.first()
        }

        assertThat(replayed).isNull()
    }

    private fun loggedInSession() = AuthSession(
        cookieHeader = "test-cookie",
        username = "test-user",
    )
}
