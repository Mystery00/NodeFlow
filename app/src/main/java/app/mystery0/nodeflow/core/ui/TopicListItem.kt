package app.mystery0.nodeflow.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.NodeChip
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.Topic

@Composable
fun TopicListItem(
    topic: Topic,
    onClick: () -> Unit,
    onNodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodeChip = topicNodeChip(topic)
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(
                avatarUrl = topic.avatarUrl,
                username = topic.author.username,
                modifier = Modifier.clickable(onClick = onClick),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = topic.title,
                    modifier = Modifier.clickable(onClick = onClick),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    nodeChip?.let { chip ->
                        NodeChip(
                            title = chip.label,
                            onClick = { onNodeClick(chip.nodeName) },
                        )
                    }
                    Text(
                        text = buildString {
                            if (topic.author.username.isNotBlank()) {
                                append(topic.author.username)
                            }
                            val time = formatEpochSeconds(topic.lastTouchedAtEpochSeconds ?: topic.createdAtEpochSeconds)
                            if (time.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(time)
                            }
                        },
                        modifier = Modifier.clickable(onClick = onClick),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (topic.replyCount > 0) {
                Spacer(Modifier.width(8.dp))
                Badge(
                    modifier = Modifier.clickable(onClick = onClick),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Text(topic.replyCount.toString())
                }
            }
        }
        HorizontalDivider()
    }
}
