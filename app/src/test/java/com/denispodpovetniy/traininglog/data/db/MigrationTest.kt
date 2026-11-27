package com.denispodpovetniy.traininglog.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Миграция 1 → 2: добавляется колонка `units`, существующие данные не меняются.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val testDb = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun `migration 1 to 2 adds units and keeps existing data`() {
        helper.createDatabase(testDb, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO exercises (id, name, description, lastProgress, createdAt, updatedAt)
                VALUES (1, 'Присед', 'описание', '20/6-20/6', 100, 200)
                """.trimIndent(),
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            testDb,
            2,
            true,
            Migrations.MIGRATION_1_2,
        )

        migrated.query("SELECT name, description, lastProgress, units FROM exercises").use { cursor ->
            cursor.moveToFirst()
            assertEquals("Присед", cursor.getString(0))
            assertEquals("описание", cursor.getString(1))
            assertEquals("20/6-20/6", cursor.getString(2))
            assertEquals("", cursor.getString(3))
        }
        migrated.close()
    }
}
