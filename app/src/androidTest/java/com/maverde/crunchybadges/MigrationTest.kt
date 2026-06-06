package com.maverde.crunchybadges

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test-db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AnimeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate3To4_createsPlatformsTable_andBackfillsCrunchyrollRows() {
        // Create v3 and seed one series row.
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO series (id, title, description, type, is_new, added_timestamp, updated_timestamp) " +
                    "VALUES ('GR1', 'Naruto', 'd', 'series', 0, 0, 0)"
            )
            close()
        }

        // Run migration to v4.
        val db = helper.runMigrationsAndValidate(
            dbName, 4, true, AnimeDatabase.MIGRATION_3_4
        )

        val cursor = db.query(
            "SELECT platform FROM series_platforms WHERE series_id = 'GR1'"
        )
        assertTrue(cursor.moveToFirst())
        assertEquals("crunchyroll", cursor.getString(0))
        cursor.close()
    }
}
