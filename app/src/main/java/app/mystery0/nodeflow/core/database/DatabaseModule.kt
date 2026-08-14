package app.mystery0.nodeflow.core.database

import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.dao.UserDao
import app.mystery0.nodeflow.core.database.dao.ReplyDraftDao
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            NodeFlowDatabase::class.java,
            "nodeflow.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

    single<ReplyDraftDao> {
        get<NodeFlowDatabase>().replyDraftDao()
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

internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reply_drafts (
                topicId INTEGER NOT NULL,
                content TEXT NOT NULL,
                selectionStart INTEGER NOT NULL,
                selectionEnd INTEGER NOT NULL,
                updatedAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(topicId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reply_draft_images (
                imageId TEXT NOT NULL,
                topicId INTEGER NOT NULL,
                originalUrl TEXT NOT NULL,
                detailUrl TEXT NOT NULL,
                originalFileName TEXT NOT NULL,
                createdAtEpochMillis INTEGER NOT NULL,
                PRIMARY KEY(imageId),
                FOREIGN KEY(topicId) REFERENCES reply_drafts(topicId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_reply_draft_images_topicId ON reply_draft_images(topicId)",
        )
    }
}

internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE topics ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
    }
}
