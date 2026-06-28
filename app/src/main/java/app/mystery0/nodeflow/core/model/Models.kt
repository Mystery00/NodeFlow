package app.mystery0.nodeflow.core.model

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
)

data class AuthSession(
    val personalAccessToken: String? = null,
    val cookieHeader: String? = null,
    val username: String? = null,
)

data class User(
    val id: Long? = null,
    val username: String,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val tagline: String? = null,
    val website: String? = null,
    val github: String? = null,
    val location: String? = null,
    val createdAtEpochSeconds: Long? = null,
)

data class Node(
    val id: Long? = null,
    val name: String,
    val title: String,
    val header: String? = null,
    val avatarUrl: String? = null,
    val topics: Int? = null,
    val stars: Int? = null,
)

data class Topic(
    val id: Long,
    val title: String,
    val url: String,
    val node: Node,
    val author: User,
    val avatarUrl: String? = author.avatarUrl,
    val replyCount: Int = 0,
    val createdAtEpochSeconds: Long? = null,
    val lastTouchedAtEpochSeconds: Long? = null,
    val lastReplyBy: String? = null,
)

data class TopicDetail(
    val topic: Topic,
    val content: String,
    val contentRendered: String,
    val replies: List<Reply>,
)

data class Reply(
    val id: Long,
    val topicId: Long,
    val floor: Int,
    val author: User,
    val content: String,
    val contentRendered: String,
    val createdAtEpochSeconds: Long? = null,
    val thanks: Int = 0,
)

data class Notification(
    val id: Long,
    val title: String,
    val content: String,
    val createdAtEpochSeconds: Long? = null,
    val unread: Boolean = false,
)
