package app.mystery0.nodeflow.core.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.mystery0.nodeflow.core.database.dao.NodeDao
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
        ).addMigrations(MIGRATION_1_2)
            .fallbackToDestructiveMigration(false)
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
}

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE users ADD COLUMN memberNumber INTEGER")
        db.execSQL("ALTER TABLE users ADD COLUMN dailyActivityRank INTEGER")
    }
}
