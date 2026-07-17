package app.mystery0.nodeflow.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mystery0.nodeflow.core.designsystem.component.HtmlText
import app.mystery0.nodeflow.core.designsystem.component.UserAvatar
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.ReplyReference

@Composable
fun ReplyItem(
    reply: Reply,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    isTopicAuthor: Boolean = false,
    onReferenceClick: (ReplyReference) -> Unit = {},
    onImageClick: (String) -> Unit = {},
    onUrlClick: (String) -> Boolean = { false },
) {
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        label = "reply-highlight-color",
    )
    Column(modifier = modifier) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = reply.author.username,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (isTopicAuthor) {
                            Spacer(Modifier.width(6.dp))
                            TopicAuthorBadge()
                        }
                    }
                    val time = formatEpochSeconds(reply.createdAtEpochSeconds)
                    Text(
                        text = if (time.isBlank()) "#${reply.floor}" else "#${reply.floor} · $time",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            reply.reference?.let { reference ->
                ReplyReferencePreview(
                    reference = reference,
                    onClick = onReferenceClick,
                )
            }
            HtmlText(
                html = reply.contentRendered,
                onImageClick = onImageClick,
                onUrlClick = { url ->
                    val reference = reply.reference
                    val username = memberUsernameFromUrl(url)
                    if (reference != null && username != null &&
                        reference.author.username.equals(username, ignoreCase = true)
                    ) {
                        onReferenceClick(reference)
                        true
                    } else {
                        onUrlClick(url)
                    }
                },
            )
        }
        }
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
        )
    }
}

/** 楼主标识：与用户名并排的小徽标。 */
@Composable
private fun TopicAuthorBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Text(
            text = "楼主",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

/**
 * 回复是否来自楼主。V2EX 用户名不区分大小写；
 * 作者名解析失败为空时不能与空用户名误判为同一人。
 */
fun isReplyFromTopicAuthor(replyUsername: String, topicAuthorUsername: String): Boolean =
    replyUsername.isNotBlank() &&
            topicAuthorUsername.isNotBlank() &&
            replyUsername.trim().equals(topicAuthorUsername.trim(), ignoreCase = true)

internal fun memberUsernameFromUrl(url: String): String? {
    val cleanUrl = url
        .substringBefore('?')
        .substringBefore('#')
        .trimEnd('/')
    val prefix = V2EX_MEMBER_URL_PREFIXES.firstOrNull { cleanUrl.startsWith(it) } ?: return null
    return cleanUrl.removePrefix(prefix)
        .takeIf { it.isNotBlank() && '/' !in it }
}

private val V2EX_MEMBER_URL_PREFIXES = listOf(
    "/member/",
    "https://www.v2ex.com/member/",
    "http://www.v2ex.com/member/",
)

@Composable
private fun ReplyReferencePreview(
    reference: ReplyReference,
    onClick: (ReplyReference) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(reference) },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "#${reference.floor} · ${reference.author.username}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reference.excerpt,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
