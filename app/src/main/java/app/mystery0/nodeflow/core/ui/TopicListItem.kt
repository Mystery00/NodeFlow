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
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.Topic

@Composable
fun TopicListItem(
    topic: Topic,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            UserAvatar(
                avatarUrl = topic.avatarUrl,
                username = topic.author.username,
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = topic.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (topic.node.title.isNotBlank()) append(topic.node.title)
                        if (topic.author.username.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(topic.author.username)
                        }
                        val time = formatEpochSeconds(topic.lastTouchedAtEpochSeconds ?: topic.createdAtEpochSeconds)
                        if (time.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(time)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (topic.replyCount > 0) {
                Spacer(Modifier.width(8.dp))
                Badge {
                    Text(topic.replyCount.toString())
                }
            }
        }
        HorizontalDivider()
    }
}
