package app.mystery0.nodeflow.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
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
fun HomeScreen(
    state: HomeUiState,
    topics: LazyPagingItems<Topic>,
    onEvent: (HomeUiEvent) -> Unit,
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                actions = {
                    IconButton(onClick = { onEvent(HomeUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        val refreshState = topics.loadState.refresh
        when {
            refreshState is LoadState.Loading && topics.itemCount == 0 -> LoadingContent(
                paddingValues = paddingValues,
            )
            refreshState is LoadState.Error && topics.itemCount == 0 -> ErrorContent(
                message = refreshState.error.toUserMessage(),
                onRetry = { onEvent(HomeUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            refreshState is LoadState.NotLoading && topics.itemCount == 0 -> EmptyContent(
                message = "暂无主题",
                paddingValues = paddingValues,
            )
            else -> TopicList(
                topics = topics,
                isRefreshing = refreshState is LoadState.Loading,
                onRetry = { topics.retry() },
                onTopicClick = onTopicClick,
                onNodeClick = onNodeClick,
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun TopicList(
    topics: LazyPagingItems<Topic>,
    isRefreshing: Boolean,
    onRetry: () -> Unit,
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 80.dp,
            ),
        ) {
            items(
                count = topics.itemCount,
                key = { index -> topics.peek(index)?.id ?: "home-topic-placeholder-$index" },
            ) { index ->
                topics[index]?.let { topic ->
                    TopicListItem(
                        topic = topic,
                        onClick = { onTopicClick(topic) },
                        onNodeClick = onNodeClick,
                    )
                }
            }
            when (val appendState = topics.loadState.append) {
                is LoadState.Loading -> item(key = "home-topics-append-loading") {
                    NodeFlowHorizontalRefreshIndicator(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
                is LoadState.Error -> item(key = "home-topics-append-error") {
                    AppendErrorContent(
                        message = appendState.error.toUserMessage(),
                        onRetry = onRetry,
                    )
                }
                is LoadState.NotLoading -> Unit
            }
        }
        ListRefreshIndicator(
            isRefreshing = isRefreshing,
            itemCount = topics.itemCount,
            topPadding = contentPadding.calculateTopPadding(),
        )
    }
}

@Composable
private fun AppendErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRetry) {
            Text("重试")
        }
    }
}
