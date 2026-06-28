package app.mystery0.nodeflow.data.user

import app.mystery0.nodeflow.core.database.dao.UserDao
import app.mystery0.nodeflow.core.database.entity.toEntity
import app.mystery0.nodeflow.core.database.entity.toUser
import app.mystery0.nodeflow.core.model.User

class UserLocalDataSource(
    private val userDao: UserDao,
) {
    suspend fun user(username: String): User? = userDao.user(username)?.toUser()

    suspend fun cacheUser(user: User) {
        userDao.upsertUser(user.toEntity())
    }

    suspend fun clear() {
        userDao.clear()
    }
}
