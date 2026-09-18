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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.R
import app.mystery0.nodeflow.core.designsystem.component.NodeChip
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
    val dividerStartPadding: Dp,
    val dividerEndPadding: Dp,
    val dividerThickness: Dp,
) {
    val dividerHorizontalPadding: Dp
        get() = dividerEndPadding
}

fun compactTopicListItemLayout(): TopicListItemLayout = TopicListItemLayout(
    horizontalPadding = 16.dp,
    verticalPadding = 12.dp,
    avatarSize = 40.dp,
    avatarCornerRadius = 8.dp,
    avatarToContentSpacing = 12.dp,
    contentSpacing = 6.dp,
    metadataSpacing = 8.dp,
    replyBadgeSpacing = 12.dp,
    dividerStartPadding = 68.dp,
    dividerEndPadding = 16.dp,
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
    val votesLabel = topicVotesChip(topic)
    val layout = compactTopicListItemLayout()
    val backgroundColor = if (topic.isPinned) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor),
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
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(layout.metadataSpacing),
                ) {
                    if (topic.isPinned) {
                        TopicPinnedBadge()
                    }
                    nodeChip?.let { chip ->
                        NodeChip(
                            title = chip.label,
                            onClick = { onNodeClick(chip.nodeName) },
                        )
                    }
                    Text(
                        text = topicListMetadataWithLastReply(topic),
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (topic.replyCount > 0 || votesLabel != null) {
                Spacer(Modifier.width(layout.replyBadgeSpacing))
                Column(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (topic.replyCount > 0) {
                        ReplyCountBadge(replyCount = topic.replyCount)
                    }
                    votesLabel?.let { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(
                start = layout.dividerStartPadding,
                end = layout.dividerEndPadding,
            ),
            thickness = layout.dividerThickness,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
    }
}

@Composable
private fun TopicPinnedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        contentColor = MaterialTheme.colorScheme.primary,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = stringResource(R.string.topic_pinned),
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.topic_pinned),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
            )
        }
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
}

fun topicLastReplyLabel(topic: Topic): String? {
    val lastReplyBy = topic.lastReplyBy?.trim().orEmpty()
    if (lastReplyBy.isEmpty()) return null
    return "最后回复来自 $lastReplyBy"
}

fun topicListMetadataWithLastReply(topic: Topic): String = buildString {
    append(topicListMetadata(topic))
    val lastReply = topicLastReplyLabel(topic)
    if (!lastReply.isNullOrBlank()) {
        if (isNotEmpty()) append(" · ")
        append(lastReply)
    }
}

@Composable
private fun ReplyCountBadge(
    replyCount: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .height(22.dp)
            .defaultMinSize(minWidth = 32.dp),
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = replyCount.toString(),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

