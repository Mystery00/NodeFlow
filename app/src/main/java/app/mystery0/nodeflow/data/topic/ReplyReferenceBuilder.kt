package app.mystery0.nodeflow.data.topic

import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.ReplyReference
import org.jsoup.Jsoup

internal fun List<Reply>.withReferencePreviews(): List<Reply> {
    if (isEmpty()) return this
    val repliesByFloor = associateBy { it.floor }
    return map { reply ->
        val referenceCandidate = reply.firstReferenceCandidate()
        val referencedReply = when {
            referenceCandidate?.floor != null -> referenceCandidate.floor
                .takeIf { floor -> floor in 1 until reply.floor }
                ?.let(repliesByFloor::get)
                ?.takeIf { referencedReply ->
                    referencedReply.author.username.equals(referenceCandidate.username, ignoreCase = true)
                }
            referenceCandidate != null -> takeWhile { it.floor < reply.floor }
                .lastOrNull { previousReply ->
                    previousReply.author.username.equals(referenceCandidate.username, ignoreCase = true)
                }
            else -> null
        }
        if (referencedReply == null) {
            reply
        } else {
            reply.copy(
                reference = ReplyReference(
                    replyId = referencedReply.id,
                    floor = referencedReply.floor,
                    author = referencedReply.author,
                    excerpt = referencedReply.referenceExcerpt(),
                ),
            )
        }
    }
}

private fun Reply.firstReferenceCandidate(): ReplyReferenceCandidate? {
    val match = REPLY_MENTION_REGEX.find(referenceSourceText()) ?: return null
    val username = match.groupValues.getOrNull(1)
        ?.takeIf { it.isNotBlank() }
        ?: return null
    val floor = match.groupValues.getOrNull(2)
        ?.takeIf { it.isNotBlank() }
        ?.toIntOrNull()
    return ReplyReferenceCandidate(username = username, floor = floor)
}

private fun Reply.referenceSourceText(): String =
    content.takeIf { it.isNotBlank() }
        ?: Jsoup.parseBodyFragment(contentRendered).text()

private fun Reply.referenceExcerpt(): String {
    val text = Jsoup.parseBodyFragment(contentRendered.ifBlank { content }).text()
        .replace(Regex("""\s+"""), " ")
        .trim()
    return if (text.length <= MaxReferenceExcerptLength) {
        text
    } else {
        text.take(MaxReferenceExcerptLength).trimEnd() + "..."
    }
}

private data class ReplyReferenceCandidate(
    val username: String,
    val floor: Int?,
)

private val REPLY_MENTION_REGEX =
    Regex("""(?:^|\s)@([A-Za-z0-9_][A-Za-z0-9_-]{0,31})(?:\s*#(\d{1,4}))?(?=$|[^A-Za-z0-9_-])""")
private const val MaxReferenceExcerptLength = 96
