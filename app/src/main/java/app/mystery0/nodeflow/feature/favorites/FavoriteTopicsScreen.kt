package app.mystery0.nodeflow.feature.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.ui.ListRefreshIndicator
import app.mystery0.nodeflow.core.ui.NodeFlowHorizontalRefreshIndicator
import app.mystery0.nodeflow.core.ui.TopicListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoriteTopicsScreen(
    state: FavoriteTopicsUiState,
    topics: LazyPagingItems<Topic>,
    onEvent: (FavoriteTopicsUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onTopicClick: (Long) -> Unit,
    onNodeClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LifecycleResumeEffect(Unit) {
        onEvent(FavoriteTopicsUiEvent.Resume)
        onPauseOrDispose { }
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorite_topics)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.favorites_back))
                    }
                },
                actions = {
                    if (state.isLoggedIn) {
                        IconButton(onClick = { onEvent(FavoriteTopicsUiEvent.Refresh) }) {
                            Icon(Icons.Outlined.Refresh, stringResource(R.string.favorites_refresh))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val refresh = topics.loadState.refresh
        when {
            favoriteTopicsRequiresLogin(state.isLoggedIn, refresh, topics.loadState.append) -> Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(stringResource(R.string.favorites_login_required))
                Button(onClick = onLoginClick) { Text(stringResource(R.string.favorites_login)) }
            }
            refresh is LoadState.Loading && topics.itemCount == 0 -> LoadingContent(paddingValues = padding)
            refresh is LoadState.Error && topics.itemCount == 0 -> ErrorContent(
                message = refresh.error.toUserMessage(),
                onRetry = topics::retry,
                paddingValues = padding,
            )
            refresh is LoadState.NotLoading && topics.itemCount == 0 -> EmptyContent(
                message = stringResource(R.string.favorites_empty),
                paddingValues = padding,
            )
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = padding.calculateBottomPadding() + 24.dp,
                    ),
                ) {
                    if (refresh is LoadState.Error) {
                        item(key = "refresh-error") {
                            FavoriteTopicsLoadError(refresh.error.toUserMessage(), topics::retry)
                        }
                    }
                    items(
                        count = topics.itemCount,
                        key = { topics.peek(it)?.id ?: "favorite-placeholder-$it" },
                    ) { index ->
                        topics[index]?.let { topic ->
                            TopicListItem(topic, onClick = { onTopicClick(topic.id) }, onNodeClick = onNodeClick)
                        }
                    }
                    when (val append = topics.loadState.append) {
                        is LoadState.Loading -> item(key = "append-loading") {
                            NodeFlowHorizontalRefreshIndicator(Modifier.padding(24.dp))
                        }
                        is LoadState.Error -> item(key = "append-error") {
                            FavoriteTopicsLoadError(append.error.toUserMessage(), topics::retry)
                        }
                        is LoadState.NotLoading -> Unit
                    }
                }
                ListRefreshIndicator(
                    isRefreshing = refresh is LoadState.Loading,
                    itemCount = topics.itemCount,
                    topPadding = padding.calculateTopPadding(),
                )
            }
        }
    }
}

@Composable
private fun FavoriteTopicsLoadError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text(stringResource(R.string.favorites_retry)) }
    }
}
