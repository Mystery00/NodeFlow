package app.mystery0.nodeflow.feature.node

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScreen(
    state: NodeUiState,
    topics: LazyPagingItems<Topic>,
    onEvent: (NodeUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showBlockConfirmation by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.blockNodeCompleted) {
        if (state.blockNodeCompleted) showBlockConfirmation = false
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.node?.title ?: state.nodeName) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (state.isLoggedIn) {
                                onEvent(NodeUiEvent.BlockNodeErrorConsumed)
                                showBlockConfirmation = true
                            } else {
                                onLoginClick()
                            }
                        },
                        enabled = !state.isBlockingNode,
                    ) {
                        Icon(Icons.Outlined.Block, contentDescription = "屏蔽节点")
                    }
                    IconButton(onClick = { onEvent(NodeUiEvent.TogglePinnedHomeNode) }) {
                        Icon(
                            imageVector = if (state.isPinnedHomeNode) {
                                Icons.Filled.PushPin
                            } else {
                                Icons.Outlined.PushPin
                            },
                            contentDescription = if (state.isPinnedHomeNode) "取消固定" else "固定",
                        )
                    }
                    IconButton(onClick = { onEvent(NodeUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
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
                onRetry = { onEvent(NodeUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            refreshState is LoadState.NotLoading && topics.itemCount == 0 -> EmptyContent(
                message = "暂无节点主题",
                paddingValues = paddingValues,
            )
            else -> NodeTopicList(
                state = state,
                topics = topics,
                isRefreshing = refreshState is LoadState.Loading,
                onTopicClick = onTopicClick,
                onNodeClick = onNodeClick,
                contentPadding = paddingValues,
            )
        }
    }
    if (showBlockConfirmation) {
        BlockNodeConfirmationDialog(
            nodeTitle = state.node?.title?.takeIf { it.isNotBlank() } ?: state.nodeName,
            isBlocking = state.isBlockingNode,
            errorMessage = state.blockNodeError,
            onConfirm = { onEvent(NodeUiEvent.BlockNode) },
            onDismiss = {
                onEvent(NodeUiEvent.BlockNodeErrorConsumed)
                showBlockConfirmation = false
            },
        )
    }
    if (state.blockNodeCompleted) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("节点已屏蔽") },
            text = { Text("该节点已加入 V2EX 屏蔽列表。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onEvent(NodeUiEvent.BlockNodeResultConsumed)
                        onBackClick()
                    },
                ) {
                    Text("返回")
                }
            },
        )
    }
}

@Composable
private fun BlockNodeConfirmationDialog(
    nodeTitle: String,
    isBlocking: Boolean,
    errorMessage: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isBlocking) onDismiss() },
        title = { Text("屏蔽节点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("屏蔽后，该节点的主题将不再出现在 V2EX 首页。是否屏蔽「$nodeTitle」？")
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isBlocking,
            ) {
                if (isBlocking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("屏蔽")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isBlocking,
            ) {
                Text("取消")
            }
        },
    )
}

@Composable
private fun NodeTopicList(
    state: NodeUiState,
    topics: LazyPagingItems<Topic>,
    isRefreshing: Boolean,
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
            item(key = "node-header") {
                NodeHeader(state = state)
                HorizontalDivider()
            }
            items(
                count = topics.itemCount,
                key = { index -> topics.peek(index)?.id ?: "node-topic-placeholder-$index" },
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
                is LoadState.Loading -> item(key = "node-topics-append-loading") {
                    NodeFlowHorizontalRefreshIndicator(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    )
                }
                is LoadState.Error -> item(key = "node-topics-append-error") {
                    AppendErrorContent(
                        message = appendState.error.toUserMessage(),
                        onRetry = { topics.retry() },
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
private fun NodeHeader(state: NodeUiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Start,
    ) {
        state.node?.avatarUrl?.takeIf { it.isNotBlank() }?.let { avatarUrl ->
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(12.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.node?.title ?: state.nodeName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            state.node?.header?.takeIf { it.isNotBlank() }?.let { header ->
                Text(
                    text = header,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
