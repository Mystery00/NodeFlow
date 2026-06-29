package app.mystery0.nodeflow.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.mystery0.nodeflow.feature.auth.AuthScreen
import app.mystery0.nodeflow.feature.auth.AuthViewModel
import app.mystery0.nodeflow.feature.editor.EditorScreen
import app.mystery0.nodeflow.feature.editor.EditorViewModel
import app.mystery0.nodeflow.feature.home.HomeScreen
import app.mystery0.nodeflow.feature.home.HomeViewModel
import app.mystery0.nodeflow.feature.node.NodeScreen
import app.mystery0.nodeflow.feature.node.NodeViewModel
import app.mystery0.nodeflow.feature.notification.NotificationScreen
import app.mystery0.nodeflow.feature.notification.NotificationViewModel
import app.mystery0.nodeflow.feature.profile.ProfileScreen
import app.mystery0.nodeflow.feature.profile.ProfileViewModel
import app.mystery0.nodeflow.feature.settings.SettingsScreen
import app.mystery0.nodeflow.feature.settings.SettingsViewModel
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailScreen
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun NodeFlowNavHost(
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    Scaffold(
        modifier = modifier,
        bottomBar = {
            NodeFlowBottomBar(
                currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route,
                onHomeClick = {
                    navController.navigateTopLevel(NodeFlowDestinations.Home)
                },
                onNodeClick = {
                    navController.navigateTopLevel(NodeFlowDestinations.node())
                },
                onSettingsClick = {
                    navController.navigateTopLevel(NodeFlowDestinations.Settings)
                },
            )
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = NodeFlowDestinations.Home,
            modifier = Modifier.padding(paddingValues),
        ) {
            composable(NodeFlowDestinations.Home) {
                val viewModel: HomeViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                HomeScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onTopicClick = { topic ->
                        navController.navigate(NodeFlowDestinations.topic(topic.id))
                    },
                )
            }
            composable(
                route = NodeFlowDestinations.NodeRoute,
                arguments = listOf(navArgument("nodeName") { type = NavType.StringType }),
            ) {
                val viewModel: NodeViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                NodeScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onTopicClick = { topic ->
                        navController.navigate(NodeFlowDestinations.topic(topic.id))
                    },
                )
            }
            composable(
                route = NodeFlowDestinations.TopicRoute,
                arguments = listOf(navArgument("topicId") { type = NavType.LongType }),
            ) {
                val viewModel: TopicDetailViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                TopicDetailScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onBackClick = { navController.popBackStack() },
                    onNodeClick = { nodeName ->
                        navController.navigate(NodeFlowDestinations.node(nodeName))
                    },
                    onUserClick = { username ->
                        navController.navigate(NodeFlowDestinations.profile(username))
                    },
                )
            }
            composable(
                route = NodeFlowDestinations.ProfileRoute,
                arguments = listOf(navArgument("username") { type = NavType.StringType }),
            ) {
                val viewModel: ProfileViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                ProfileScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onBackClick = { navController.popBackStack() },
                )
            }
            composable(NodeFlowDestinations.Settings) {
                val viewModel: SettingsViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                )
            }
            composable(NodeFlowDestinations.Auth) {
                val viewModel: AuthViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                AuthScreen(state = state, onEvent = viewModel::onEvent)
            }
            composable(NodeFlowDestinations.Notification) {
                val viewModel: NotificationViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                NotificationScreen(
                    state = state,
                    onEvent = viewModel::onEvent,
                )
            }
            composable(NodeFlowDestinations.Editor) {
                val viewModel: EditorViewModel = koinViewModel()
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                EditorScreen(state = state, onEvent = viewModel::onEvent)
            }
        }
    }
}

@Composable
private fun NodeFlowBottomBar(
    currentRoute: String?,
    onHomeClick: () -> Unit,
    onNodeClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentRoute == NodeFlowDestinations.Home,
            onClick = onHomeClick,
            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
            label = { Text("首页") },
        )
        NavigationBarItem(
            selected = currentRoute == NodeFlowDestinations.NodeRoute,
            onClick = onNodeClick,
            icon = { Icon(Icons.Outlined.AccountTree, contentDescription = null) },
            label = { Text("节点") },
        )
        NavigationBarItem(
            selected = currentRoute == NodeFlowDestinations.Settings,
            onClick = onSettingsClick,
            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
            label = { Text("设置") },
        )
    }
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(NodeFlowDestinations.Home) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}
