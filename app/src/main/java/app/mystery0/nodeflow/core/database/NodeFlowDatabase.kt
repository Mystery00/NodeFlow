package app.mystery0.nodeflow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.dao.UserDao
import app.mystery0.nodeflow.core.database.dao.ReplyDraftDao
import app.mystery0.nodeflow.core.database.entity.NodeEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneEntity
import app.mystery0.nodeflow.core.database.entity.NodePlaneNodeEntity
import app.mystery0.nodeflow.core.database.entity.TopicEntity
import app.mystery0.nodeflow.core.database.entity.UserEntity
import app.mystery0.nodeflow.core.database.entity.ReplyDraftEntity
import app.mystery0.nodeflow.core.database.entity.ReplyDraftImageEntity

@Database(
    entities = [
        TopicEntity::class,
        NodeEntity::class,
        NodePlaneEntity::class,
        NodePlaneNodeEntity::class,
        UserEntity::class,
        ReplyDraftEntity::class,
        ReplyDraftImageEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class NodeFlowDatabase : RoomDatabase() {
    abstract fun topicDao(): TopicDao
    abstract fun nodeDao(): NodeDao
    abstract fun userDao(): UserDao
    abstract fun replyDraftDao(): ReplyDraftDao
}
