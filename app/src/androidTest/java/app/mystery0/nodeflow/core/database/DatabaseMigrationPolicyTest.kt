package app.mystery0.nodeflow.core.database

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import app.mystery0.nodeflow.core.database.entity.ReplyDraftEntity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.koinApplication
import java.io.File
import java.util.UUID

/** 使用生产 DI 配置验证持久化策略；数据库位于独立临时目录，不接触应用数据库。 */
class DatabaseMigrationPolicyTest {
    private lateinit var context: Context
    private lateinit var directory: File

    @Before
    fun setUp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        directory = File(target.cacheDir, "database-policy-${UUID.randomUUID()}")
        check(directory.mkdirs())
        context = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getDatabasePath(name: String): File = File(directory, name)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory)
            override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?): SQLiteDatabase =
                SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).path, factory, errorHandler)
            override fun deleteDatabase(name: String): Boolean = SQLiteDatabase.deleteDatabase(getDatabasePath(name))
        }
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun currentSchemaPreservesDraftAfterReopen() = runBlocking {
        withProductionDatabase { database ->
            database.replyDraftDao().upsertDraft(sampleDraft())
        }
        withProductionDatabase { database ->
            val draft = database.replyDraftDao().draft(42L)
            assertThat(draft?.draft?.content).isEqualTo("待发送的回复")
            assertThat(draft?.draft?.selectionStart).isEqualTo(2)
        }
    }

    @Test
    fun missingDowngradeMigrationRejectsOpenAndPreservesDraft() = runBlocking {
        withProductionDatabase { database ->
            database.replyDraftDao().upsertDraft(sampleDraft())
        }
        // 模拟由更新版本写入的数据库；不修改生产版本或 schema。
        SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.version = 2
        }

        val failure = runCatching {
            withProductionDatabase { database ->
                database.replyDraftDao().draft(42L)
            }
        }.exceptionOrNull()
        assertThat(failure).isInstanceOf(IllegalStateException::class.java)

        SQLiteDatabase.openDatabase(context.getDatabasePath(DATABASE_NAME).path, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            assertThat(database.version).isEqualTo(2)
            database.rawQuery("SELECT content FROM reply_drafts WHERE topicId = 42", null).use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("待发送的回复")
            }
        }
    }

    private suspend fun withProductionDatabase(block: suspend (NodeFlowDatabase) -> Unit) {
        val application = koinApplication {
            androidContext(context)
            modules(databaseModule)
        }
        val database = application.koin.get<NodeFlowDatabase>()
        try {
            block(database)
        } finally {
            database.close()
            application.close()
        }
    }

    private fun sampleDraft() = ReplyDraftEntity(
        topicId = 42L,
        content = "待发送的回复",
        selectionStart = 2,
        selectionEnd = 2,
        updatedAtEpochMillis = 1L,
    )

    private companion object {
        const val DATABASE_NAME = "nodeflow.db"
    }
}
