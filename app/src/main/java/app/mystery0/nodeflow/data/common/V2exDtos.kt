package app.mystery0.nodeflow.data.common

import app.mystery0.nodeflow.core.model.Node
import app.mystery0.nodeflow.core.model.Reply
import app.mystery0.nodeflow.core.model.Topic
import app.mystery0.nodeflow.core.model.User
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class V2exMemberDto(
    val id: Long? = null,
    val username: String? = null,
    val url: String? = null,
    val website: String? = null,
    val github: String? = null,
    val location: String? = null,
    val tagline: String? = null,
    val bio: String? = null,
    @SerialName("avatar_mini") val avatarMini: String? = null,
    @SerialName("avatar_normal") val avatarNormal: String? = null,
    @SerialName("avatar_large") val avatarLarge: String? = null,
    @SerialName("avatar_xlarge") val avatarXLarge: String? = null,
    val created: Long? = null,
)

@Serializable
data class V2exNodeDto(
    val id: Long? = null,
    val name: String? = null,
    val title: String? = null,
    @SerialName("title_alternative") val titleAlternative: String? = null,
    val header: String? = null,
    @SerialName("avatar_normal") val avatarNormal: String? = null,
    @SerialName("avatar_large") val avatarLarge: String? = null,
    val topics: Int? = null,
    val stars: Int? = null,
)

@Serializable
data class V2exTopicDto(
    val id: Long,
    val title: String,
    val url: String? = null,
    val content: String? = null,
    @SerialName("content_rendered") val contentRendered: String? = null,
    val replies: Int? = null,
    val created: Long? = null,
    @SerialName("last_modified") val lastModified: Long? = null,
    @SerialName("last_touched") val lastTouched: Long? = null,
    val member: V2exMemberDto? = null,
    val node: V2exNodeDto? = null,
)

@Serializable
data class V2exReplyDto(
    val id: Long,
    @SerialName("topic_id") val topicId: Long? = null,
    val content: String? = null,
    @SerialName("content_rendered") val contentRendered: String? = null,
    val created: Long? = null,
    val thanks: Int? = null,
    val member: V2exMemberDto? = null,
)

fun V2exMemberDto.toUser(): User = User(
    id = id,
    memberNumber = id,
    username = username.orEmpty(),
    avatarUrl = avatarXLarge ?: avatarLarge ?: avatarNormal ?: avatarMini,
    bio = bio,
    tagline = tagline,
    website = website,
    github = github,
    location = location,
    createdAtEpochSeconds = created,
)

fun V2exNodeDto.toNode(): Node = Node(
    id = id,
    name = name.orEmpty(),
    title = title ?: titleAlternative ?: name.orEmpty(),
    header = header,
    avatarUrl = avatarLarge ?: avatarNormal,
    topics = topics,
    stars = stars,
)

fun V2exTopicDto.toTopic(): Topic {
    val topicNode = node?.toNode() ?: Node(name = "", title = "")
    val topicAuthor = member?.toUser() ?: User(username = "")
    return Topic(
        id = id,
        title = title,
        url = url ?: "https://www.v2ex.com/t/$id",
        node = topicNode,
        author = topicAuthor,
        avatarUrl = topicAuthor.avatarUrl,
        replyCount = replies ?: 0,
        createdAtEpochSeconds = created,
        lastTouchedAtEpochSeconds = lastTouched ?: lastModified,
    )
}

fun V2exReplyDto.toReply(topicIdFallback: Long, floor: Int): Reply {
    val replyAuthor = member?.toUser() ?: User(username = "")
    return Reply(
        id = id,
        topicId = topicId ?: topicIdFallback,
        floor = floor,
        author = replyAuthor,
        content = content.orEmpty(),
        contentRendered = contentRendered ?: content.orEmpty(),
        createdAtEpochSeconds = created,
        thanks = thanks ?: 0,
    )
}
