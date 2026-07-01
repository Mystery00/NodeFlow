package app.mystery0.nodeflow.feature.topicdetail

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.LocalOffer
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.RichHtmlText
import app.mystery0.nodeflow.core.designsystem.component.ZoomableImageViewer
import app.mystery0.nodeflow.core.model.TopicDetail
import app.mystery0.nodeflow.core.ui.NodeFlowHorizontalRefreshIndicator
import app.mystery0.nodeflow.core.ui.ReplyItem
import app.mystery0.nodeflow.core.ui.formatEpochSeconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SecondsPerMinute = 60L
private const val SecondsPerHour = 60L * SecondsPerMinute
private const val SecondsPerDay = 24L * SecondsPerHour

internal fun topicMetadataText(
    username: String,
    time: String,
    viewCount: Int?,
): String = buildList {
    username.takeIf { it.isNotBlank() }?.let(::add)
    time.takeIf { it.isNotBlank() }?.let(::add)
    viewCount?.let { add("${it} 次点击") }
}.joinToString(" · ")

internal fun formatTopicMetadataTime(
    epochSeconds: Long?,
    nowEpochSeconds: Long = java.time.Instant.now().epochSecond,
): String {
    if (epochSeconds == null || epochSeconds <= 0) return ""
    val elapsedSeconds = nowEpochSeconds - epochSeconds
    if (elapsedSeconds < SecondsPerMinute) return "刚刚"
    if (elapsedSeconds < SecondsPerHour) return "${elapsedSeconds / SecondsPerMinute} 分钟前"
    if (elapsedSeconds < SecondsPerDay) {
        val hours = elapsedSeconds / SecondsPerHour
        val minutes = elapsedSeconds % SecondsPerHour / SecondsPerMinute
        return if (minutes > 0) {
            "${hours} 小时 ${minutes} 分钟前"
        } else {
            "${hours} 小时前"
        }
    }
    return formatEpochSeconds(epochSeconds, nowEpochSeconds)
}

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
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                scrollBehavior = scrollBehavior,
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
                onImageClick = { previewImageUrl = it },
                contentPadding = paddingValues,
            )
        }
    }
    previewImageUrl?.let { imageUrl ->
        ZoomableImageViewer(
            imageUrl = imageUrl,
            onDismiss = { previewImageUrl = null },
        )
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
    onImageClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var highlightedReplyId by remember(detail.topic.id) { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (isRefreshing) {
            NodeFlowHorizontalRefreshIndicator(Modifier.fillMaxWidth())
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
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
                    TopicMetadataRow(
                        detail = detail,
                        onUserClick = onUserClick,
                    )
                    RichHtmlText(
                        html = detail.contentRendered,
                        onImageClick = onImageClick,
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
                ReplySummaryRow(detail = detail)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                )
            }
            items(detail.replies, key = { it.id }) { reply ->
                ReplyItem(
                    reply = reply,
                    highlighted = highlightedReplyId == reply.id,
                    onImageClick = onImageClick,
                    onReferenceClick = { reference ->
                        val targetIndex = detail.replies.indexOfFirst { it.id == reference.replyId }
                        if (targetIndex >= 0) {
                            coroutineScope.launch {
                                listState.animateScrollToItem(index = targetIndex + 1)
                                highlightedReplyId = reference.replyId
                                delay(1400)
                                if (highlightedReplyId == reference.replyId) {
                                    highlightedReplyId = null
                                }
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun TopicMetadataRow(
    detail: TopicDetail,
    onUserClick: (String) -> Unit,
) {
    val username = detail.topic.author.username
    val time = formatTopicMetadataTime(detail.topic.createdAtEpochSeconds)
    val items = topicMetadataText(username, time, detail.viewCount).split(" · ")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { index, text ->
            if (index > 0) {
                MetadataText(text = " · ")
            }
            if (index == 0 && username.isNotBlank()) {
                Text(
                    text = text,
                    modifier = Modifier.clickable { onUserClick(username) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                MetadataText(text = text)
            }
        }
    }
}

@Composable
private fun ReplySummaryRow(detail: TopicDetail) {
    val replyCount = detail.topic.replyCount.takeIf { it > 0 } ?: detail.replies.size
    val summaryText = buildString {
        append(replyCount)
        append(" 条回复")
        detail.hotReplyCount?.takeIf { it > 0 }?.let { count ->
            append(" · ")
            append(count)
            append(" 条热门回复")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summaryText,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detail.tags.isNotEmpty()) {
            Spacer(Modifier.width(12.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                detail.tags.forEach { tag ->
                    TopicTagChip(tag = tag)
                }
            }
        }
    }
}

@Composable
private fun MetadataText(
    text: String,
    onClick: (() -> Unit)? = null,
) {
    if (onClick == null) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Surface(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TopicTagChip(tag: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.LocalOffer,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
            )
            Text(
                text = tag,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
