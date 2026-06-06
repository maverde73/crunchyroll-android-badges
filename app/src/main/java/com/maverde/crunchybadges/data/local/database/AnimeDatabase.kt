package com.maverde.crunchybadges.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.maverde.crunchybadges.data.local.dao.AnimeDao
import com.maverde.crunchybadges.data.local.entities.*

/**
 * Room Database for anime catalog
 * Version 1: Initial release with single anime entity
 * Version 2: Normalized schema with 9 tables for better data management
 */
@Database(
    entities = [
        Series::class,
        SeriesMetadata::class,
        Rating::class,
        Image::class,
        Award::class,
        AudioLocale::class,
        SubtitleLocale::class,
        ContentDescriptor::class,
        MaturityRatingEntity::class,
        TranslatedDescription::class,
        SeriesPlatform::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AnimeDatabase : RoomDatabase() {

    abstract fun animeDao(): AnimeDao

    companion object {
        @Volatile
        private var INSTANCE: AnimeDatabase? = null

        /**
         * Migration 3 -> 4: add series_platforms table + external id columns.
         * Retro-populates a 'crunchyroll' platform row for every existing series.
         * Replaces the destructive wipe for this schema change.
         */
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE series ADD COLUMN mal_id INTEGER")
                db.execSQL("ALTER TABLE series ADD COLUMN anilist_id INTEGER")
                db.execSQL("ALTER TABLE series ADD COLUMN tmdb_id INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS series_platforms (
                        series_id TEXT NOT NULL,
                        platform TEXT NOT NULL,
                        deep_link_url TEXT,
                        audio_locales TEXT NOT NULL DEFAULT '',
                        subtitle_locales TEXT NOT NULL DEFAULT '',
                        languages_assumed INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(series_id, platform),
                        FOREIGN KEY(series_id) REFERENCES series(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_series_platforms_series_id ON series_platforms(series_id)")
                // Every pre-existing series came from Crunchyroll.
                db.execSQL("INSERT OR IGNORE INTO series_platforms (series_id, platform) SELECT id, 'crunchyroll' FROM series")
            }
        }

        /**
         * Get database instance (singleton)
         */
        fun getDatabase(context: Context): AnimeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AnimeDatabase::class.java,
                    "anime_database"
                )
                    .addMigrations(MIGRATION_3_4)
                    .fallbackToDestructiveMigration()  // Backstop for unhandled jumps only
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
