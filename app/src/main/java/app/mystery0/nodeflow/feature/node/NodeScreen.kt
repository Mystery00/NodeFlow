package app.mystery0.nodeflow.feature.node

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.ui.ListRefreshIndicator
import app.mystery0.nodeflow.core.ui.TopicListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScreen(
    state: NodeUiState,
    onEvent: (NodeUiEvent) -> Unit,
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.node?.title ?: state.nodeName) },
                actions = {
                    IconButton(onClick = { onEvent(NodeUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(paddingValues = paddingValues)
            state.errorMessage != null && state.topics.isEmpty() -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(NodeUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            state.topics.isEmpty() -> EmptyContent(
                message = "暂无节点主题",
                paddingValues = paddingValues,
            )
            else -> NodeTopicList(
                state = state,
                onTopicClick = onTopicClick,
                onNodeClick = onNodeClick,
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun NodeTopicList(
    state: NodeUiState,
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
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
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
                HorizontalDivider()
            }
            items(state.topics, key = { it.id }) { topic ->
                TopicListItem(
                    topic = topic,
                    onClick = { onTopicClick(topic) },
                    onNodeClick = onNodeClick,
                )
            }
        }
        ListRefreshIndicator(
            isRefreshing = state.isRefreshing,
            itemCount = state.topics.size,
            topPadding = contentPadding.calculateTopPadding(),
        )
    }
}
