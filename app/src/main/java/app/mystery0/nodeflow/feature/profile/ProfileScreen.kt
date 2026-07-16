package app.mystery0.nodeflow.feature.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PersonOff
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.EmptyContent
import app.mystery0.nodeflow.core.designsystem.component.ErrorContent
import app.mystery0.nodeflow.core.designsystem.component.LoadingContent
import app.mystery0.nodeflow.core.designsystem.component.NodeChip
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.ProfileReply
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.ui.TopicListItem
import app.mystery0.nodeflow.core.ui.formatEpochSeconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    onBackClick: () -> Unit,
    onTopicClick: (Long) -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(state.username) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(ProfileUiEvent.Refresh) }) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { paddingValues ->
        when {
            state.isLoading -> LoadingContent(paddingValues = paddingValues)
            state.userNotFound && state.user == null -> EmptyContent(
                message = "该用户不存在，或账号已被停用",
                paddingValues = paddingValues,
                icon = Icons.Outlined.PersonOff,
            )
            state.errorMessage != null && state.user == null -> ErrorContent(
                message = state.errorMessage,
                onRetry = { onEvent(ProfileUiEvent.Retry) },
                paddingValues = paddingValues,
            )
            state.user != null -> ProfileContent(
                state = state,
                user = state.user,
                onTopicClick = onTopicClick,
                onNodeClick = onNodeClick,
                contentPadding = paddingValues,
            )
        }
    }
}

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    user: User,
    onTopicClick: (Long) -> Unit,
    onNodeClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp,
        ),
    ) {
        item(key = "profile-header") {
            ProfileHeader(user = user)
        }
        if (state.recentTopics.isNotEmpty()) {
            item(key = "topics-section") {
                SectionHeader(title = "最近发布的主题")
            }
            items(state.recentTopics, key = { "topic-${it.id}" }) { topic ->
                TopicListItem(
                    topic = topic,
                    onClick = { onTopicClick(topic.id) },
                    onNodeClick = onNodeClick,
                )
            }
        }
        if (state.recentReplies.isNotEmpty()) {
            item(key = "replies-section") {
                SectionHeader(title = "最近的回复")
            }
            itemsIndexed(
                items = state.recentReplies,
                key = { index, reply -> "reply-$index-${reply.topicId}" },
            ) { _, reply ->
                ProfileReplyItem(
                    reply = reply,
                    onClick = { onTopicClick(reply.topicId) },
                    onNodeClick = onNodeClick,
                )
            }
        }
        if (state.recentTopics.isEmpty() && state.recentReplies.isEmpty()) {
            item(key = "empty-activity") {
                Text(
                    text = "暂无公开的主题和回复",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(user: User) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UserAvatar(
            avatarUrl = user.avatarUrl,
            username = user.username,
            size = 88.dp,
        )
        Text(
            text = user.username,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        user.tagline?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        user.bio?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        ProfileLine(label = "会员", value = formatMemberNumber(user.memberNumber))
        ProfileLine(label = "今日活跃度排名", value = formatDailyActivityRank(user.dailyActivityRank))
        ProfileLine(label = "加入时间", value = formatEpochSeconds(user.createdAtEpochSeconds))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun ProfileReplyItem(
    reply: ProfileReply,
    onClick: () -> Unit,
    onNodeClick: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = reply.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reply.nodeName.takeIf { it.isNotBlank() }?.let { nodeName ->
                NodeChip(
                    title = reply.nodeTitle.takeIf { it.isNotBlank() } ?: nodeName,
                    onClick = { onNodeClick(nodeName) },
                )
            }
            Text(
                text = reply.topicTitle,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            formatEpochSeconds(reply.createdAtEpochSeconds).takeIf { it.isNotBlank() }?.let { time ->
                Spacer(Modifier.width(4.dp))
                Text(
                    text = time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            thickness = 0.5.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
    }
}

private fun formatMemberNumber(memberNumber: Long?): String? =
    memberNumber?.let { "V2EX 第 ${it.formatCount()} 号会员" }

private fun formatDailyActivityRank(rank: Int?): String? =
    rank?.let { "第 ${it.formatCount()} 名" }

private fun Long.formatCount(): String = "%,d".format(this)

private fun Int.formatCount(): String = "%,d".format(this)

@Composable
private fun ProfileLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Text(
        text = "$label：$value",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium,
    )
}
