package app.mystery0.nodeflow.data.notification

import app.mystery0.nodeflow.core.model.Notification
import app.mystery0.nodeflow.core.model.ReplyReference
import app.mystery0.nodeflow.core.model.TopicDetail
import kotlinx.coroutines.CancellationException
import org.jsoup.Jsoup

/**
 * 为通知补全被引用回复的摘要。
 *
 * [loadRepliesUntilFloor] 按（主题, 楼层）加载至少覆盖目标楼层的回复前缀；
 * 同一批次内某主题一旦加载失败，其余指向该主题的通知不再重试。
 */
internal suspend fun enrichNotificationReferences(
    notifications: List<Notification>,
    loadRepliesUntilFloor: suspend (topicId: Long, floor: Int) -> Result<TopicDetail>,
): List<Notification> {
    val failedTopics = mutableSetOf<Long>()
    return notifications.map { notification ->
        val locator = notification.referenceLocator ?: return@map notification
        if (notification.topicId in failedTopics) return@map notification
        val detail = try {
            loadRepliesUntilFloor(notification.topicId, locator.floor).getOrNull()
        } catch (error: CancellationException) {
            throw error
        }
        if (detail == null) {
            failedTopics += notification.topicId
            return@map notification
        }
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
