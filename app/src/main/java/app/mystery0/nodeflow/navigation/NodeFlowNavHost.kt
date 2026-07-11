package app.mystery0.nodeflow.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.feature.auth.AuthScreen
import app.mystery0.nodeflow.feature.auth.AuthViewModel
import app.mystery0.nodeflow.feature.editor.EditorScreen
import app.mystery0.nodeflow.feature.editor.EditorViewModel
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
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = rootStartDestination(),
        modifier = modifier,
        enterTransition = { rootEnterTransition() },
        exitTransition = { rootExitTransition() },
        popEnterTransition = { rootPopEnterTransition() },
        popExitTransition = { rootPopExitTransition() },
    ) {
        composable(NodeFlowDestinations.Main) {
            MainShell(
                settings = settings,
                onTopicClick = { topic ->
                    navController.navigate(NodeFlowDestinations.topic(topic.id))
                },
                onNodeClick = { nodeName ->
                    navController.navigate(NodeFlowDestinations.node(nodeName))
                },
                onSettingsClick = {
                    navController.navigate(NodeFlowDestinations.Settings)
                },
                onLoginClick = {
                    navController.navigate(NodeFlowDestinations.Auth)
                },
            )
        }
        composable(NodeFlowDestinations.Settings) {
            val viewModel: SettingsViewModel = koinViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            SettingsScreen(
                state = state,
                onEvent = viewModel::onEvent,
                onBackClick = { navController.popBackStack() },
            )
        }
        composable(
            route = NodeFlowDestinations.NodeRoute,
            arguments = listOf(navArgument("nodeName") { type = NavType.StringType }),
        ) {
            val viewModel: NodeViewModel = koinViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val topics = viewModel.topics.collectAsLazyPagingItems()
            NodeScreen(
                state = state,
                topics = topics,
                onEvent = viewModel::onEvent,
                onBackClick = { navController.popBackStack() },
                onTopicClick = { topic ->
                    navController.navigate(NodeFlowDestinations.topic(topic.id))
                },
                onNodeClick = { nodeName ->
                    navController.navigate(NodeFlowDestinations.node(nodeName))
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
        composable(NodeFlowDestinations.Auth) {
            val viewModel: AuthViewModel = koinViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            AuthScreen(
                state = state,
                onEvent = viewModel::onEvent,
                onBackClick = { navController.popBackStack() },
                onLoginSuccess = { navController.popBackStack() },
            )
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

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootEnterTransition(): EnterTransition {
    val targetRoute = targetState.destination.route
    return if (NodeFlowDestinations.isRootDetailRoute(targetRoute)) {
        slideInHorizontally(initialOffsetX = { it }) + fadeIn()
    } else {
        EnterTransition.None
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootExitTransition(): ExitTransition {
    return ExitTransition.None
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootPopEnterTransition(): EnterTransition {
    return EnterTransition.None
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.rootPopExitTransition(): ExitTransition {
    val initialRoute = initialState.destination.route
    return if (NodeFlowDestinations.isRootDetailRoute(initialRoute)) {
        slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    } else {
        ExitTransition.None
    }
}
