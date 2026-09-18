package app.mystery0.nodeflow.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.designsystem.component.NodeChip
import app.mystery0.nodeflow.core.designsystem.component.StatusChip
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.Topic

data class TopicListItemLayout(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val avatarSize: Dp,
    val avatarCornerRadius: Dp,
    val avatarToContentSpacing: Dp,
    val contentSpacing: Dp,
    val metadataSpacing: Dp,
    val replyBadgeSpacing: Dp,
    val dividerHorizontalPadding: Dp,
    val dividerThickness: Dp,
)

fun compactTopicListItemLayout(): TopicListItemLayout = TopicListItemLayout(
    horizontalPadding = 16.dp,
    verticalPadding = 12.dp,
    avatarSize = 40.dp,
    avatarCornerRadius = 6.dp,
    avatarToContentSpacing = 12.dp,
    contentSpacing = 4.dp,
    metadataSpacing = 8.dp,
    replyBadgeSpacing = 12.dp,
    dividerHorizontalPadding = 16.dp,
    dividerThickness = 0.5.dp,
)

@Composable
fun TopicListItem(
    topic: Topic,
    onClick: () -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeChip = topicNodeChip(topic)
    val pinnedChip = topicPinnedChip(topic, stringResource(R.string.topic_pinned))
    val votesChip = topicVotesChip(topic)
    val layout = compactTopicListItemLayout()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = layout.horizontalPadding, vertical = layout.verticalPadding),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(
                avatarUrl = topic.avatarUrl,
                username = topic.author.username,
                size = layout.avatarSize,
                shape = RoundedCornerShape(layout.avatarCornerRadius),
            )
            Spacer(Modifier.width(layout.avatarToContentSpacing))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(layout.contentSpacing),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.metadataSpacing),
                ) {
                    pinnedChip?.let { label ->
                        StatusChip(title = label)
                    }
                    votesChip?.let { label ->
                        StatusChip(title = label)
                    }
                    nodeChip?.let { chip ->
                        NodeChip(
                            title = chip.label,
                            onClick = { onNodeClick(chip.nodeName) },
                        )
                    }
                    Text(
                        text = topicListMetadata(topic),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (topic.replyCount > 0) {
                Spacer(Modifier.width(layout.replyBadgeSpacing))
                ReplyCountBadge(replyCount = topic.replyCount)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = layout.dividerHorizontalPadding),
            thickness = layout.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
    }
}

fun topicListMetadata(topic: Topic): String = buildString {
    if (topic.author.username.isNotBlank()) {
        append(topic.author.username)
    }
    val time = formatEpochSeconds(topic.lastTouchedAtEpochSeconds ?: topic.createdAtEpochSeconds)
    if (time.isNotBlank()) {
        if (isNotEmpty()) append(" · ")
        append(time)
    }
    if (!topic.lastReplyBy.isNullOrBlank()) {
        if (isNotEmpty()) append(" · ")
        append("最后回复来自 ")
        append(topic.lastReplyBy)
    }
}

@Composable
private fun ReplyCountBadge(
    replyCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(24.dp)
            .defaultMinSize(minWidth = 32.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = replyCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
            )
        }
    }
}
