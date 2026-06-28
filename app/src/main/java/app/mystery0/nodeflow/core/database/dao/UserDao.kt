package app.mystery0.nodeflow.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import app.mystery0.nodeflow.core.database.entity.UserEntity

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun user(username: String): UserEntity?

    @Upsert
    suspend fun upsertUser(user: UserEntity)

    @Query("DELETE FROM users")
    suspend fun clear()
}
