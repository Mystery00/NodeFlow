package app.mystery0.nodeflow.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.paging.compose.collectAsLazyPagingItems
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.feature.account.AccountScreen
import app.mystery0.nodeflow.feature.account.AccountViewModel
import app.mystery0.nodeflow.feature.home.HomeScreen
import app.mystery0.nodeflow.feature.home.HomeViewModel
import app.mystery0.nodeflow.feature.node.NodeListScreen
import app.mystery0.nodeflow.feature.node.NodeListViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun MainShell(
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NodeFlowBottomBar(
                currentRoute = currentRoute,
                onHomeClick = {
                    navController.navigateTopLevel(NodeFlowDestinations.Home)
                },
                onNodeClick = {
                    navController.navigateTopLevel(nodeBottomBarRoute())
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
            composable(NodeFlowDestinations.Account) {
                val viewModel: AccountViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AccountScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
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
    onHomeClick: () -> Unit,
    onNodeClick: () -> Unit,
    onAccountClick: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == NodeFlowDestinations.Home,
            onClick = onHomeClick,
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            label = { Text("首页") },
        )
        NavigationBarItem(
            selected = isNodeBottomBarSelected(currentRoute),
            onClick = onNodeClick,
            icon = { Icon(Icons.Outlined.AccountTree, contentDescription = null) },
            label = { Text("节点") },
        )
        NavigationBarItem(
            selected = isAccountBottomBarSelected(currentRoute),
            onClick = onAccountClick,
            icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            label = { Text("我的") },
        )
    }
}

fun nodeBottomBarRoute(): String = NodeFlowDestinations.NodeList

fun isNodeBottomBarSelected(currentRoute: String?): Boolean = currentRoute == NodeFlowDestinations.NodeList

fun accountBottomBarRoute(): String = NodeFlowDestinations.Account

fun isAccountBottomBarSelected(currentRoute: String?): Boolean = currentRoute == NodeFlowDestinations.Account

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(NodeFlowDestinations.Home) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
