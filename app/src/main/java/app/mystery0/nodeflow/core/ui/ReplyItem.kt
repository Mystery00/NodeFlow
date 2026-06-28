package app.mystery0.nodeflow.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.HtmlText
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.Reply

@Composable
fun ReplyItem(
    reply: Reply,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(
                    avatarUrl = reply.author.avatarUrl,
                    username = reply.author.username,
                    size = 32.dp,
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reply.author.username,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val time = formatEpochSeconds(reply.createdAtEpochSeconds)
                    Text(
                        text = if (time.isBlank()) "#${reply.floor}" else "#${reply.floor} · $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HtmlText(html = reply.contentRendered)
        }
        HorizontalDivider()
    }
}
