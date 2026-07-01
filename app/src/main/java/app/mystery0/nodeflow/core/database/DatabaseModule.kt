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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)
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

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS node_planes (
                name TEXT NOT NULL,
                title TEXT NOT NULL,
                nodeCount INTEGER,
                avatarUrl TEXT,
                sortOrder INTEGER NOT NULL,
                cachedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(name)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS node_plane_nodes (
                planeName TEXT NOT NULL,
                nodeName TEXT NOT NULL,
                sortOrder INTEGER NOT NULL,
                cachedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(planeName, nodeName),
                FOREIGN KEY(planeName) REFERENCES node_planes(name) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(nodeName) REFERENCES nodes(name) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_node_plane_nodes_planeName ON node_plane_nodes(planeName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_node_plane_nodes_nodeName ON node_plane_nodes(nodeName)")
    }
}
