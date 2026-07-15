package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.model.ReplyReference
import app.mystery0.nodeflow.core.model.TopicDetail
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup

internal suspend fun enrichNotificationReferences(
    notifications: List<Notification>,
    loadTopic: suspend (Long) -> Result<TopicDetail>,
): List<Notification> {
    val details = mutableMapOf<Long, TopicDetail?>()
    return notifications.map { notification ->
        val locator = notification.referenceLocator ?: return@map notification
        val detail = if (details.containsKey(notification.topicId)) {
            details[notification.topicId]
        } else {
            val loaded = try {
                loadTopic(notification.topicId).getOrNull()
            } catch (error: CancellationException) {
                throw error
            }
            details[notification.topicId] = loaded
            loaded
        } ?: return@map notification
        val reply = detail.replies.firstOrNull { reply ->
            reply.floor == locator.floor &&
                reply.author.username.equals(locator.username, ignoreCase = true)
        } ?: return@map notification
        notification.copy(
            reference = ReplyReference(
                replyId = reply.id,
                floor = reply.floor,
                author = reply.author,
                excerpt = reply.referenceExcerpt(),
            ),
        )
    }
}

private fun app.mystery0.nodeflow.core.model.Reply.referenceExcerpt(): String {
    val text = Jsoup.parseBodyFragment(contentRendered.ifBlank { content }).text()
        .replace(Regex("""\s+"""), " ")
        .trim()
    return if (text.length <= MAX_EXCERPT_LENGTH) text else text.take(MAX_EXCERPT_LENGTH).trimEnd() + "..."
}

private const val MAX_EXCERPT_LENGTH = 96
