package app.mystery0.nodeflow.feature.notification

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import app.mystery0.nodeflow.core.common.toUserMessage
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.HtmlText
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.designsystem.component.ZoomableImageViewer
import app.mystery0.nodeflow.core.link.V2exLink
import app.mystery0.nodeflow.core.link.V2exLinkParser
import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.model.ReplyReference
import app.mystery0.nodeflow.core.ui.ListRefreshIndicator
import app.mystery0.nodeflow.core.ui.NodeFlowHorizontalRefreshIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationScreen(
    notifications: LazyPagingItems<Notification>,
    onEvent: (NotificationUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onUserClick: (String) -> Unit,
    onTopicClick: (Long, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var previewImageUrl by remember { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("通知") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(NotificationUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { paddingValues ->
        val refreshState = notifications.loadState.refresh
        when {
            refreshState is LoadState.Loading && notifications.itemCount == 0 ->
                LoadingContent(paddingValues = paddingValues)
            refreshState is LoadState.Error && notifications.itemCount == 0 ->
                ErrorContent(
                    message = refreshState.error.toUserMessage(),
                    onRetry = notifications::retry,
                    paddingValues = paddingValues,
                )
            refreshState is LoadState.NotLoading && notifications.itemCount == 0 ->
                EmptyContent(message = "暂无通知", paddingValues = paddingValues)
            else -> NotificationList(
                notifications = notifications,
                contentPadding = paddingValues,
                onUserClick = onUserClick,
                onTopicClick = onTopicClick,
                onImageClick = { previewImageUrl = it },
            )
        }
    }
    previewImageUrl?.let { imageUrl ->
        ZoomableImageViewer(imageUrl = imageUrl, onDismiss = { previewImageUrl = null })
    }
}

@Composable
private fun NotificationList(
    notifications: LazyPagingItems<Notification>,
    contentPadding: PaddingValues,
    onUserClick: (String) -> Unit,
    onTopicClick: (Long, Int?) -> Unit,
    onImageClick: (String) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            items(
                count = notifications.itemCount,
                key = { index -> notifications.peek(index)?.id ?: "notification-$index" },
            ) { index ->
                notifications[index]?.let { notification ->
                    NotificationItem(
                        notification = notification,
                        onUserClick = onUserClick,
                        onTopicClick = onTopicClick,
                        onImageClick = onImageClick,
                    )
                }
            }
            when (val appendState = notifications.loadState.append) {
                is LoadState.Loading -> item {
                    NodeFlowHorizontalRefreshIndicator(Modifier.padding(24.dp))
                }
                is LoadState.Error -> item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(appendState.error.toUserMessage(), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = notifications::retry) { Text("重试") }
                    }
                }
                is LoadState.NotLoading -> Unit
            }
        }
        ListRefreshIndicator(
            isRefreshing = notifications.loadState.refresh is LoadState.Loading,
            itemCount = notifications.itemCount,
            topPadding = contentPadding.calculateTopPadding(),
        )
    }
}

@Composable
private fun NotificationItem(
    notification: Notification,
    onUserClick: (String) -> Unit,
    onTopicClick: (Long, Int?) -> Unit,
    onImageClick: (String) -> Unit,
) {
    val openUrl: (String) -> Boolean = { url ->
        when (val link = V2exLinkParser.parse(url)) {
            is V2exLink.Topic -> {
                onTopicClick(link.id, null)
                true
            }
            is V2exLink.Member -> {
                onUserClick(link.username)
                true
            }
            else -> false
        }
    }
    Column {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(
                    avatarUrl = notification.actor.avatarUrl,
                    username = notification.actor.username,
                    size = 36.dp,
                    modifier = Modifier.clickable { onUserClick(notification.actor.username) },
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = notification.actor.username,
                        modifier = Modifier.clickable { onUserClick(notification.actor.username) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = listOf(notification.action, notification.relativeTime)
                            .filter { it.isNotBlank() }
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = notification.topicTitle,
                modifier = Modifier.clickable {
                    onTopicClick(notification.topicId, notification.replyFloor)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            notification.reference?.let { reference ->
                NotificationReferenceCard(
                    reference = reference,
                    onClick = { onTopicClick(notification.topicId, reference.floor) },
                )
            }
            notification.contentRendered?.takeIf { it.isNotBlank() }?.let { content ->
                HtmlText(html = content, onUrlClick = openUrl, onImageClick = onImageClick)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
    }
}

@Composable
private fun NotificationReferenceCard(
    reference: ReplyReference,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "#${reference.floor} · ${reference.author.username}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = reference.excerpt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
