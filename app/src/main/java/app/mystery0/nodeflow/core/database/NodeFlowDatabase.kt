package app.mystery0.nodeflow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.dao.UserDao
import app.mystery0.nodeflow.core.database.entity.NodeEntity
import app.mystery0.nodeflow.core.database.entity.TopicEntity
import app.mystery0.nodeflow.core.database.entity.UserEntity

@Database(
    entities = [
        TopicEntity::class,
        NodeEntity::class,
        UserEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class NodeFlowDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun nodeDao(): NodeDao
    abstract fun userDao(): UserDao
}
