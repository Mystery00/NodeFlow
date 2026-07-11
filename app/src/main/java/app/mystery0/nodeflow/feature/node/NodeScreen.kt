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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.ui.ListRefreshIndicator
import app.mystery0.nodeflow.core.ui.TopicListItem
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodeScreen(
    state: NodeUiState,
    onEvent: (NodeUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onTopicClick: (Topic) -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
