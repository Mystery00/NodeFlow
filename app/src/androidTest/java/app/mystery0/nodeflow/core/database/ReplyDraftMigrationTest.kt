package app.mystery0.nodeflow.core.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReplyDraftMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NodeFlowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate3To4_preservesExistingDataAndCascadesDraftImages() {
        helper.createDatabase(TEST_DB, 3).apply {
            execSQL(
                """
                INSERT INTO topics(
                    id, title, url, nodeName, nodeTitle, authorName,
                    replyCount, cachedAtEpochMillis
                ) VALUES(
                    42, '保留主题', 'https://www.v2ex.com/t/42', 'sandbox',
                    'Sandbox', 'tester', 0, 1
                )
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 4, true, MIGRATION_3_4)
        db.execSQL("PRAGMA foreign_keys=ON")
        assertThat(queryCount(db, "topics")).isEqualTo(1)
        db.execSQL(
            "INSERT INTO reply_drafts(topicId, content, selectionStart, selectionEnd, updatedAtEpochMillis) VALUES(42, 'draft', 5, 5, 1)",
        )
        db.execSQL(
            "INSERT INTO reply_draft_images(imageId, topicId, originalUrl, detailUrl, originalFileName, createdAtEpochMillis) VALUES('img', 42, 'https://i.v2ex.co/img.png', 'https://www.v2ex.com/i/img.png', 'img.png', 1)",
        )
        db.execSQL("DELETE FROM reply_drafts WHERE topicId = 42")
        assertThat(queryCount(db, "reply_draft_images")).isEqualTo(0)
        db.close()
    }

    private fun queryCount(db: androidx.sqlite.db.SupportSQLiteDatabase, table: String): Int =
        db.query("SELECT COUNT(*) FROM $table").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }

    private companion object {
        const val TEST_DB = "reply-draft-migration-test"
    }
}
