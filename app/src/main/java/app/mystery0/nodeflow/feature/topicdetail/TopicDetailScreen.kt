package app.mystery0.nodeflow.feature.topicdetail

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.HtmlText
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.NodeChip
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.ui.ReplyItem
import app.mystery0.nodeflow.core.ui.formatEpochSeconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicDetailScreen(
    state: TopicDetailUiState,
    onEvent: (TopicDetailUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onNodeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val detail = state.detail
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("主题详情") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(TopicDetailUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                    if (detail != null) {
                        TopicActions(detail)
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(paddingValues = paddingValues)
            state.errorMessage != null && detail == null -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(TopicDetailUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            detail == null -> EmptyContent(
                message = "主题不存在",
                paddingValues = paddingValues,
            )
            else -> TopicDetailContent(
                detail = detail,
                isRefreshing = state.isRefreshing,
                onNodeClick = onNodeClick,
                onUserClick = onUserClick,
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun TopicActions(detail: TopicDetail) {
    val context = LocalContext.current
    val url = detail.topic.url
    IconButton(
        onClick = {
            val systemClipboard = context.getSystemService(android.content.ClipboardManager::class.java)
            systemClipboard?.setPrimaryClip(ClipData.newPlainText("NodeFlow topic", url))
        },
    ) {
        Icon(Icons.Outlined.ContentCopy, contentDescription = "复制链接")
    }
    IconButton(
        onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, "${detail.topic.title}\n$url")
            }
            context.startActivity(Intent.createChooser(intent, "分享主题"))
        },
    ) {
        Icon(Icons.Outlined.Share, contentDescription = "分享")
    }
}

@Composable
private fun TopicDetailContent(
    detail: TopicDetail,
    isRefreshing: Boolean,
    onNodeClick: (String) -> Unit,
    onUserClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize()) {
        if (isRefreshing) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = detail.topic.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (detail.topic.node.name.isNotBlank()) {
                            NodeChip(
                                title = detail.topic.node.title.ifBlank { detail.topic.node.name },
                                onClick = { onNodeClick(detail.topic.node.name) },
                            )
                        }
                    }
                    Text(
                        text = buildString {
                            append(detail.topic.author.username)
                            val time = formatEpochSeconds(detail.topic.createdAtEpochSeconds)
                            if (time.isNotBlank()) append(" · ").append(time)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HtmlText(html = detail.contentRendered)
                }
                HorizontalDivider()
                Text(
                    text = "回复 ${detail.replies.size}",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                HorizontalDivider()
            }
            items(detail.replies, key = { it.id }) { reply ->
                ReplyItem(reply = reply)
            }
        }
    }
}
