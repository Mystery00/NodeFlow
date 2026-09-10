package app.mystery0.nodeflow.navigation

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.paging.compose.collectAsLazyPagingItems
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.link.V2exLink
import app.mystery0.nodeflow.core.model.AppSettings
import app.mystery0.nodeflow.feature.auth.AuthScreen
import app.mystery0.nodeflow.feature.auth.AuthViewModel
import app.mystery0.nodeflow.feature.favorites.FavoriteTopicsScreen
import app.mystery0.nodeflow.feature.favorites.FavoriteTopicsViewModel
import app.mystery0.nodeflow.feature.node.NodeScreen
import app.mystery0.nodeflow.feature.node.NodeViewModel
import app.mystery0.nodeflow.feature.profile.ProfileScreen
import app.mystery0.nodeflow.feature.profile.ProfileViewModel
import app.mystery0.nodeflow.feature.replyeditor.ReplyEditorEffect
import app.mystery0.nodeflow.feature.replyeditor.ReplyEditorViewModel
import app.mystery0.nodeflow.feature.settings.SettingsScreen
import app.mystery0.nodeflow.feature.settings.SettingsViewModel
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailScreen
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailUiEvent
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun NodeFlowNavHost(
    settings: AppSettings,
    modifier: Modifier = Modifier,
    deepLink: V2exLink? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    LaunchedEffect(deepLink) {
        if (deepLink != null) {
            navController.navigate(NodeFlowDestinations.routeFor(deepLink))
            onDeepLinkConsumed()
        }
    }
    NavHost(
        navController = navController,
        startDestination = rootStartDestination(),
        modifier = modifier,
        enterTransition = { activityLikeEnterTransition() },
        exitTransition = { activityLikeExitTransition() },
        popEnterTransition = { activityLikePopEnterTransition() },
        popExitTransition = { activityLikePopExitTransition() },
        predictivePopEnterTransition = { activityLikePopEnterTransition() },
        predictivePopExitTransition = { activityLikePopExitTransition() },
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
                onUserClick = { username ->
                    navController.navigate(NodeFlowDestinations.profile(username))
                },
                onNotificationTopicClick = { topicId, replyFloor ->
                    navController.navigate(NodeFlowDestinations.topic(topicId, replyFloor))
                },
                onSettingsClick = {
                    navController.navigate(NodeFlowDestinations.Settings)
                },
                onFavoriteTopicsClick = {
                    navController.navigate(NodeFlowDestinations.FavoriteTopics)
                },
                onLoginClick = {
                    navController.navigate(NodeFlowDestinations.Auth)
                },
            )
        }
        composable(NodeFlowDestinations.FavoriteTopics) {
            val viewModel: FavoriteTopicsViewModel = koinViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val topics = viewModel.topics.collectAsLazyPagingItems()
            FavoriteTopicsScreen(
                state = state,
                topics = topics,
                onEvent = viewModel::onEvent,
                onBackClick = { navController.popBackStack() },
                onTopicClick = { navController.navigate(NodeFlowDestinations.topic(it)) },
                onNodeClick = { navController.navigate(NodeFlowDestinations.node(it)) },
                onLoginClick = { navController.navigate(NodeFlowDestinations.Auth) },
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
                onLoginClick = {
                    navController.navigate(NodeFlowDestinations.Auth)
                },
            )
        }
        composable(
            route = NodeFlowDestinations.TopicRoute,
            arguments = listOf(
                navArgument("topicId") { type = NavType.LongType },
                navArgument("replyFloor") {
                    type = NavType.IntType
                    defaultValue = -1
                },
            ),
        ) { backStackEntry ->
            val viewModel: TopicDetailViewModel = koinViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val replyEditorViewModel: ReplyEditorViewModel = koinViewModel()
            val replyEditorState by replyEditorViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val uriHandler = LocalUriHandler.current
            LaunchedEffect(replyEditorViewModel, viewModel) {
                replyEditorViewModel.effects.collect { effect ->
                    when (effect) {
                        ReplyEditorEffect.RequestLogin ->
                            navController.navigate(NodeFlowDestinations.Auth)

                        ReplyEditorEffect.OpenGallery ->
                            uriHandler.openUri("https://www.v2ex.com/i")

                        is ReplyEditorEffect.ReplyCreated -> {
                            Toast.makeText(context, R.string.reply_created, Toast.LENGTH_SHORT)
                                .show()
                            viewModel.onEvent(TopicDetailUiEvent.ReplyCreated(effect.floor))
                        }
                    }
                }
            }
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
                onTopicClick = { topicId ->
                    navController.navigate(NodeFlowDestinations.topic(topicId))
                },
                initialReplyFloor = backStackEntry.arguments
                    ?.getInt("replyFloor")
                    ?.takeIf { it > 0 },
                replyEditorState = replyEditorState,
                onReplyEditorEvent = replyEditorViewModel::onEvent,
                onLoginClick = { navController.navigate(NodeFlowDestinations.Auth) },
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
                onTopicClick = { topicId ->
                    navController.navigate(NodeFlowDestinations.topic(topicId))
                },
                onNodeClick = { nodeName ->
                    navController.navigate(NodeFlowDestinations.node(nodeName))
                },
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
    }
}

// 类 Activity 的视差转场：前台页从右侧滑入并轻微放大，背景页向左位移并缩小，
// 配合 Navigation 的可 seek 转场即为预见式返回动画。
private fun activityLikeEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = ACTIVITY_FOREGROUND_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
    ) +
            fadeIn(animationSpec = tween(durationMillis = NAV_ENTER_FADE_DURATION_MS)) +
            scaleIn(
                initialScale = ACTIVITY_FOREGROUND_INITIAL_SCALE,
                animationSpec = tween(
                    durationMillis = ACTIVITY_FOREGROUND_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
}

private fun activityLikeExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> -fullWidth / ACTIVITY_BACKGROUND_OFFSET_DIVISOR },
        animationSpec = tween(
            durationMillis = ACTIVITY_BACKGROUND_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
    ) +
            activityBackgroundFadeOut() +
            scaleOut(
                targetScale = ACTIVITY_BACKGROUND_TARGET_SCALE,
                animationSpec = tween(
                    durationMillis = ACTIVITY_BACKGROUND_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
}

private fun activityLikePopEnterTransition(): EnterTransition {
    return slideInHorizontally(
        initialOffsetX = { fullWidth -> -fullWidth / ACTIVITY_BACKGROUND_OFFSET_DIVISOR },
        animationSpec = tween(
            durationMillis = ACTIVITY_BACKGROUND_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
    ) +
            fadeIn(animationSpec = tween(durationMillis = NAV_ENTER_FADE_DURATION_MS)) +
            scaleIn(
                initialScale = ACTIVITY_BACKGROUND_TARGET_SCALE,
                animationSpec = tween(
                    durationMillis = ACTIVITY_BACKGROUND_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
}

private fun activityLikePopExitTransition(): ExitTransition {
    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = tween(
            durationMillis = ACTIVITY_FOREGROUND_DURATION_MS,
            easing = FastOutSlowInEasing,
        ),
    ) +
            activityForegroundFadeOut() +
            scaleOut(
                targetScale = ACTIVITY_FOREGROUND_POP_EXIT_SCALE,
                animationSpec = tween(
                    durationMillis = ACTIVITY_FOREGROUND_DURATION_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
}

private fun activityBackgroundFadeOut(): ExitTransition {
    return fadeOut(
        animationSpec = tween(
            durationMillis = NAV_EXIT_FADE_DURATION_MS,
            delayMillis = NAV_EXIT_FADE_DELAY_MS,
        ),
    )
}

private fun activityForegroundFadeOut(): ExitTransition {
    return fadeOut(
        animationSpec = tween(
            durationMillis = NAV_EXIT_FADE_DURATION_MS,
            delayMillis = NAV_FOREGROUND_EXIT_FADE_DELAY_MS,
        ),
    )
}

private const val ACTIVITY_FOREGROUND_DURATION_MS = 300
private const val ACTIVITY_BACKGROUND_DURATION_MS = 260
private const val ACTIVITY_BACKGROUND_OFFSET_DIVISOR = 4
private const val ACTIVITY_FOREGROUND_INITIAL_SCALE = 0.96f
private const val ACTIVITY_FOREGROUND_POP_EXIT_SCALE = 0.90f
private const val ACTIVITY_BACKGROUND_TARGET_SCALE = 0.92f
private const val NAV_ENTER_FADE_DURATION_MS = 90
private const val NAV_EXIT_FADE_DURATION_MS = 90
private const val NAV_EXIT_FADE_DELAY_MS = 120
private const val NAV_FOREGROUND_EXIT_FADE_DELAY_MS = 210
