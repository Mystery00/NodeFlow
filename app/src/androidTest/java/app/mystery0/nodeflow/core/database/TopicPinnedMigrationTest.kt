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
class TopicPinnedMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        NodeFlowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate4To5_preservesTopicAndDefaultsPinnedStateToFalse() {
        helper.createDatabase(TEST_DB, 4).apply {
            execSQL(
                """
                INSERT INTO topics(
                    id, title, url, nodeName, nodeTitle, authorName,
                    replyCount, cachedAtEpochMillis
                ) VALUES(
                    42, '保留主题', 'https://www.v2ex.com/t/42', 'android',
                    'Android', 'alice', 0, 1
                )
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_4_5)
        db.query("SELECT title, isPinned FROM topics WHERE id = 42").use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.getString(0)).isEqualTo("保留主题")
            assertThat(cursor.getInt(1)).isEqualTo(0)
        }
        db.close()
    }

    private companion object {
        const val TEST_DB = "topic-pinned-migration-test"
    }
}
