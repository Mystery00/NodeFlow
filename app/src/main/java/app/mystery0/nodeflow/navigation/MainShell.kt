package app.mystery0.nodeflow.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.core.model.PinnedHomeNode
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.feature.account.AccountScreen
import app.mystery0.nodeflow.feature.account.AccountUiEvent
import app.mystery0.nodeflow.feature.account.AccountUiState
import app.mystery0.nodeflow.feature.account.AccountViewModel
import app.mystery0.nodeflow.feature.home.HomeScreen
import app.mystery0.nodeflow.feature.home.HomeViewModel
import app.mystery0.nodeflow.feature.node.NodeListScreen
import app.mystery0.nodeflow.feature.node.NodeListViewModel
import app.mystery0.nodeflow.feature.notification.NotificationScreen
import app.mystery0.nodeflow.feature.notification.NotificationSignedOutScreen
import app.mystery0.nodeflow.feature.notification.NotificationViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainShell(
    settings: AppSettings,
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    onNotificationTopicClick: (Long, Int?) -> Unit,
    onSettingsClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val homeReselectRequests = remember { HomeReselectRequests() }
    val accountViewModel: AccountViewModel = koinViewModel()
    val accountState by accountViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NodeFlowBottomBar(
                currentRoute = currentRoute,
                pinnedHomeNode = settings.pinnedHomeNode,
                messageBadgeText = messageBottomBarBadgeText(accountState),
                showAccountCheckInBadge = shouldShowAccountCheckInBadge(accountState),
                onHomeClick = {
                    when (homeBottomBarAction(currentRoute)) {
                        HomeBottomBarAction.NavigateHome ->
                            navController.navigateTopLevel(NodeFlowDestinations.Home)
                        HomeBottomBarAction.ReselectHome -> homeReselectRequests.request()
                    }
                },
                onNodeClick = {
                    navController.navigateTopLevel(nodeBottomBarRoute())
                },
                onMessageClick = {
                    accountViewModel.onEvent(AccountUiEvent.NotificationsOpened)
                    navController.navigateTopLevel(messageBottomBarRoute())
                },
                onAccountClick = {
                    navController.navigateTopLevel(accountBottomBarRoute())
                },
            )
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NodeFlowDestinations.Home,
            modifier = Modifier.padding(rootNavHostPadding(paddingValues)),
        ) {
            composable(NodeFlowDestinations.Home) {
                val viewModel: HomeViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                val topics = viewModel.topics.collectAsLazyPagingItems()
                HomeScreen(
                    state = state,
                    topics = topics,
                    onEvent = viewModel::onEvent,
                    onTopicClick = onTopicClick,
                    onNodeClick = onNodeClick,
                    homeReselectEvents = homeReselectRequests.events,
                )
            }
            composable(NodeFlowDestinations.NodeList) {
                val viewModel: NodeListViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                NodeListScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onNodeClick = onNodeClick,
                )
            }
            composable(NodeFlowDestinations.Notification) {
                if (shouldLoadNotificationList(accountState)) {
                    val viewModel: NotificationViewModel = koinViewModel()
                    val notifications = viewModel.notifications.collectAsLazyPagingItems()
                    NotificationScreen(
                        notifications = notifications,
                        onEvent = viewModel::onEvent,
                        onUserClick = onUserClick,
                        onTopicClick = onNotificationTopicClick,
                    )
                } else {
                    NotificationSignedOutScreen(onLoginClick = onLoginClick)
                }
            }
            composable(NodeFlowDestinations.Account) {
                AccountScreen(
                    state = accountState,
                    onEvent = accountViewModel::onEvent,
                    onSettingsClick = onSettingsClick,
                    onLoginClick = onLoginClick,
                )
            }
        }
    }
}

fun rootNavHostPadding(scaffoldPadding: PaddingValues): PaddingValues = PaddingValues(
    bottom = scaffoldPadding.calculateBottomPadding(),
)

@Composable
private fun NodeFlowBottomBar(
    currentRoute: String?,
    pinnedHomeNode: PinnedHomeNode?,
    messageBadgeText: String?,
    showAccountCheckInBadge: Boolean,
    onHomeClick: () -> Unit,
    onNodeClick: () -> Unit,
    onMessageClick: () -> Unit,
    onAccountClick: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == NodeFlowDestinations.Home,
            onClick = onHomeClick,
            icon = { HomeBottomBarIcon(pinnedHomeNode = pinnedHomeNode) },
            label = {
                Text(
                    text = homeBottomBarLabel(pinnedHomeNode),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        NavigationBarItem(
            selected = isNodeBottomBarSelected(currentRoute),
            onClick = onNodeClick,
            icon = { Icon(Icons.Outlined.AccountTree, contentDescription = null) },
            label = { Text("节点") },
        )
        NavigationBarItem(
            selected = isMessageBottomBarSelected(currentRoute),
            onClick = onMessageClick,
            icon = {
                BadgedBox(
                    badge = {
                        messageBadgeText?.let { Badge { Text(it) } }
                    },
                ) {
                    Icon(Icons.Outlined.Notifications, contentDescription = null)
                }
            },
            label = { Text("消息") },
        )
        NavigationBarItem(
            selected = isAccountBottomBarSelected(currentRoute),
            onClick = onAccountClick,
            icon = {
                BadgedBox(
                    badge = {
                        if (showAccountCheckInBadge) {
                            Badge { Text("!") }
                        }
                    },
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null)
                }
            },
            label = { Text("我的") },
        )
    }
}

@Composable
private fun HomeBottomBarIcon(pinnedHomeNode: PinnedHomeNode?) {
    val avatarUrl = pinnedHomeNode?.avatarUrl?.takeIf { it.isNotBlank() }
    if (avatarUrl == null) {
        Icon(
            imageVector = if (pinnedHomeNode == null) Icons.Outlined.Home else Icons.Outlined.AccountTree,
            contentDescription = null,
        )
    } else {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
    }
}

fun homeBottomBarLabel(pinnedHomeNode: PinnedHomeNode?): String =
    pinnedHomeNode?.title?.takeIf { it.isNotBlank() }
        ?: pinnedHomeNode?.name?.takeIf { it.isNotBlank() }
        ?: "首页"

enum class HomeBottomBarAction {
    NavigateHome,
    ReselectHome,
}

internal class HomeReselectRequests {
    private val requests = Channel<Unit>(capacity = Channel.UNLIMITED)

    val events: Flow<Unit> = requests.receiveAsFlow()

    fun request() {
        check(requests.trySend(Unit).isSuccess)
    }
}

fun homeBottomBarAction(currentRoute: String?): HomeBottomBarAction =
    if (currentRoute == NodeFlowDestinations.Home) {
        HomeBottomBarAction.ReselectHome
    } else {
        HomeBottomBarAction.NavigateHome
    }

fun nodeBottomBarRoute(): String = NodeFlowDestinations.NodeList

fun isNodeBottomBarSelected(currentRoute: String?): Boolean = currentRoute == NodeFlowDestinations.NodeList

fun messageBottomBarRoute(): String = NodeFlowDestinations.Notification

fun isMessageBottomBarSelected(currentRoute: String?): Boolean =
    currentRoute == NodeFlowDestinations.Notification

fun messageBottomBarBadgeText(state: AccountUiState): String? {
    if (!state.isLoggedIn) return null
    val count = state.overview?.unreadNotificationCount
    return when {
        count == null && state.isLoading -> null
        count == null -> "!"
        count <= 0 -> null
        count > 99 -> "99+"
        else -> count.toString()
    }
}

fun shouldLoadNotificationList(state: AccountUiState): Boolean = state.isLoggedIn

fun accountBottomBarRoute(): String = NodeFlowDestinations.Account

fun isAccountBottomBarSelected(currentRoute: String?): Boolean = currentRoute == NodeFlowDestinations.Account

fun shouldShowAccountCheckInBadge(state: AccountUiState): Boolean =
    state.isLoggedIn && state.overview?.checkIn?.canCheckIn == true

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(NodeFlowDestinations.Home) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
