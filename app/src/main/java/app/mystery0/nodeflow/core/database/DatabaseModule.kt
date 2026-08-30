package app.mystery0.nodeflow.core.database

import androidx.room.Room
import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.dao.ReplyDraftDao
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.dao.UserDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            NodeFlowDatabase::class.java,
            "nodeflow.db",
        ).fallbackToDestructiveMigration(false)
            .build()
    }

    single<TopicDao> {
        get<NodeFlowDatabase>().topicDao()
    }

    single<NodeDao> {
        get<NodeFlowDatabase>().nodeDao()
    }

    single<UserDao> {
        get<NodeFlowDatabase>().userDao()
    }

    single<ReplyDraftDao> {
        get<NodeFlowDatabase>().replyDraftDao()
    }
}
