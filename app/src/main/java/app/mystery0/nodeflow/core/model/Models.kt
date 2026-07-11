package app.mystery0.nodeflow.core.model

enum class ThemeMode {
    System,
    Light,
    Dark,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val pinnedHomeNode: PinnedHomeNode? = null,
)

data class PinnedHomeNode(
    val name: String,
    val title: String,
    val avatarUrl: String? = null,
)

data class AuthSession(
    val personalAccessToken: String? = null,
    val cookieHeader: String? = null,
    val username: String? = null,
)

data class AccountOverview(
    val unreadNotificationCount: Int? = null,
    val checkIn: DailyCheckIn? = null,
    val wealth: AccountWealth? = null,
)

data class DailyCheckIn(
    val checkedIn: Boolean,
    val continuousDays: Int? = null,
    val redeemOnce: String? = null,
)

data class AccountWealth(
    val gold: Int? = null,
    val silver: Int? = null,
    val bronze: Int? = null,
)

data class LoginChallenge(
    val usernameField: String,
    val passwordField: String,
    val captchaField: String,
    val once: String,
    val next: String,
    val captchaPath: String,
    val captchaImageBytes: ByteArray,
)

data class TwoFactorChallenge(
    val once: String,
    val title: String,
)

sealed interface AuthLoginResult {
    data class Completed(val session: AuthSession) : AuthLoginResult
    data class TwoFactorRequired(val challenge: TwoFactorChallenge) : AuthLoginResult
}

data class User(
    val id: Long? = null,
    val memberNumber: Long? = null,
    val dailyActivityRank: Int? = null,
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

data class NodePlane(
    val name: String,
    val title: String,
    val nodeCount: Int? = null,
    val avatarUrl: String? = null,
    val nodes: List<Node> = emptyList(),
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
    val viewCount: Int? = null,
    val hotReplyCount: Int? = null,
    val tags: List<String> = emptyList(),
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
    val reference: ReplyReference? = null,
)

data class ProfileReply(
    val topicId: Long,
    val topicTitle: String,
    val nodeName: String = "",
    val nodeTitle: String = "",
    val content: String,
    val contentRendered: String,
    val createdAtEpochSeconds: Long? = null,
)

data class UserRecentActivity(
    val topics: List<Topic> = emptyList(),
    val replies: List<ProfileReply> = emptyList(),
)

data class ReplyReference(
    val replyId: Long,
    val floor: Int,
    val author: User,
    val excerpt: String,
)

data class Notification(
    val id: Long,
    val title: String,
    val content: String,
    val createdAtEpochSeconds: Long? = null,
    val unread: Boolean = false,
)
