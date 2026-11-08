package app.mystery0.nodeflow.data.user

import app.mystery0.nodeflow.core.common.runSuspendCatching
import app.mystery0.nodeflow.core.model.User
import app.mystery0.nodeflow.core.model.UserRecentActivity
import app.mystery0.nodeflow.domain.user.UserRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

class UserRepositoryImpl(
    private val remoteDataSource: UserRemoteDataSource,
    private val localDataSource: UserLocalDataSource,
    private val ioDispatcher: CoroutineDispatcher,
) : UserRepository {
    override suspend fun user(username: String, forceRefresh: Boolean): Result<User> =
        withContext(ioDispatcher) {
            runSuspendCatching {
                val cached = localDataSource.user(username)
                if (!forceRefresh && cached != null && cached.hasProfileMetadata()) return@runSuspendCatching cached
                runSuspendCatching { remoteDataSource.user(username) }
                    .onSuccess { localDataSource.cacheUser(it) }
                    .getOrElse { error -> cached ?: throw error }
            }
        }

    override suspend fun recentActivity(username: String): Result<UserRecentActivity> =
        withContext(ioDispatcher) {
            runSuspendCatching { remoteDataSource.recentActivity(username) }
        }

    override suspend fun clearCache() {
        withContext(ioDispatcher) {
            localDataSource.clear()
        }
    }

    private fun User.hasProfileMetadata(): Boolean =
        memberNumber != null && dailyActivityRank != null
}
