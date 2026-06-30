package app.mystery0.nodeflow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.mystery0.nodeflow.core.model.User

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val username: String,
    val id: Long?,
    val memberNumber: Long?,
    val dailyActivityRank: Int?,
    val avatarUrl: String?,
    val bio: String?,
    val tagline: String?,
    val website: String?,
    val github: String?,
    val location: String?,
    val createdAtEpochSeconds: Long?,
    val cachedAtEpochMillis: Long,
)

fun UserEntity.toUser(): User = User(
    id = id,
    memberNumber = memberNumber ?: id,
    dailyActivityRank = dailyActivityRank,
    username = username,
    avatarUrl = avatarUrl,
    bio = bio,
    tagline = tagline,
    website = website,
    github = github,
    location = location,
    createdAtEpochSeconds = createdAtEpochSeconds,
)

fun User.toEntity(cachedAtEpochMillis: Long = System.currentTimeMillis()): UserEntity = UserEntity(
    username = username,
    id = id,
    memberNumber = memberNumber,
    dailyActivityRank = dailyActivityRank,
    avatarUrl = avatarUrl,
    bio = bio,
    tagline = tagline,
    website = website,
    github = github,
    location = location,
    createdAtEpochSeconds = createdAtEpochSeconds,
    cachedAtEpochMillis = cachedAtEpochMillis,
)
