# Anime Generation — Android App Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing Kotlin app so it syncs the offline-published Anime Generation catalog JSON, merges it with the Crunchyroll catalog into one list with per-platform badges, and adds a platform filter.

**Architecture:** A new `series_platforms` Room table records which platform(s) each series is on. The Crunchyroll scraper stays unchanged but now also records its `crunchyroll` platform row. A new `AnimeGenerationSyncWorker` (WorkManager) downloads the catalog JSON (OkHttp), and `AnimeRepository.ingestAnimeGeneration` merges it: titles pre-matched to a Crunchyroll id (resolved offline by the pipeline) attach a platform row to the existing series; unmatched titles become standalone "AG-only" series. The grid card shows platform badges; the filter sheet gains a platform dimension. Detail view adds an "Open in Prime Video" deep link.

**Tech Stack:** Kotlin, Room 2.6.1 (KSP), kotlinx.serialization 1.6.2, OkHttp 4.12.0, WorkManager (new), Coroutines/Flow. Tests: JUnit (JVM) for pure logic; instrumented `androidTest` with `room-testing` + `work-testing` for DB/migration/worker.

**Contract:** Consumes the JSON produced by `2026-06-07-anime-generation-pipeline.md` (schema in spec §5). Package root: `com.maverde.crunchybadges`.

---

## File Structure

- Modify: `app/build.gradle.kts` — add WorkManager + test deps; enable `exportSchema=true` + schema dir
- Create: `app/src/main/java/com/maverde/crunchybadges/data/local/entities/SeriesPlatform.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/entities/Series.kt` — external id columns
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/entities/SeriesWithAllData.kt` — platforms relation + helpers
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/database/AnimeDatabase.kt` — v4 + migration + entity
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/dao/AnimeDao.kt` — platform insert/delete/query
- Create: `app/src/main/java/com/maverde/crunchybadges/data/models/AnimeGenerationCatalog.kt` — JSON models
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/repository/AnimeRepository.kt` — ingestion/merge + CR platform row
- Create: `app/src/main/java/com/maverde/crunchybadges/sync/AnimeGenerationSyncWorker.kt` — WorkManager worker
- Modify: `app/src/main/java/com/maverde/crunchybadges/ui/splash/SplashViewModel.kt` — enqueue worker after CR sync
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/models/FilterState.kt` — platform dimension
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/preferences/FilterPreferences.kt` — persist platform
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/repository/AnimeRepository.kt` — platform filter SQL
- Modify: `app/src/main/res/layout/item_anime_card.xml` + `ui/main/AnimeListAdapter.kt` — platform badges
- Modify: `app/src/main/java/com/maverde/crunchybadges/ui/filters/FilterBottomSheet.kt` + `bottom_sheet_filters.xml` — platform control
- Modify: `app/src/main/java/com/maverde/crunchybadges/IntentLauncher.kt` + `ui/detail/DetailActivity.kt` — Prime Video deep link
- Create tests under `app/src/test/...` (JVM) and `app/src/androidTest/...` (instrumented)

---

## Task 1: Dependencies + enable schema export

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add the KSP schema arg + test instrumentation runner (already present)**

In `app/build.gradle.kts`, inside `android { defaultConfig { ... } }`, add after the `testInstrumentationRunner` line:
```kotlin
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
```
And change the Room database `exportSchema` to `true` in Task 3 (the annotation lives in `AnimeDatabase.kt`).

- [ ] **Step 2: Add dependencies**

In `app/build.gradle.kts`, in the `dependencies { ... }` block, add:
```kotlin
    // WorkManager (background catalog sync)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Room migration testing
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    // WorkManager testing
    androidTestImplementation("androidx.work:work-testing:2.9.0")
    // Instrumented test helpers
    androidTestImplementation("androidx.test:core-ktx:1.5.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
```

- [ ] **Step 3: Build to confirm dependencies resolve**

Run: `./gradlew :app:dependencies --configuration debugRuntimeClasspath -q | grep -i work`
Expected: shows `androidx.work:work-runtime-ktx:2.9.0`.

- [ ] **Step 4: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: add WorkManager + Room/Work test deps, enable schema export"
```

---

## Task 2: `SeriesPlatform` entity

**Files:**
- Create: `app/src/main/java/com/maverde/crunchybadges/data/local/entities/SeriesPlatform.kt`

- [ ] **Step 1: Create the entity (mirrors the AudioLocale join-table pattern)**

`app/src/main/java/com/maverde/crunchybadges/data/local/entities/SeriesPlatform.kt`:
```kotlin
package com.maverde.crunchybadges.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Platform availability for a series (1 series -> N platforms).
 * Maps to the "series_platforms" table.
 *
 * A title available on both Crunchyroll and Anime Generation has two rows here,
 * which the UI renders as two badges. audio/subtitle locales are stored
 * comma-joined for the detail view; badge/filter logic uses the shared
 * series_audio_locales table populated during ingestion.
 */
@Entity(
    tableName = "series_platforms",
    primaryKeys = ["series_id", "platform"],
    foreignKeys = [
        ForeignKey(
            entity = Series::class,
            parentColumns = ["id"],
            childColumns = ["series_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("series_id")]
)
data class SeriesPlatform(
    @ColumnInfo(name = "series_id")
    val seriesId: String,

    val platform: String,  // PLATFORM_CRUNCHYROLL | PLATFORM_ANIME_GENERATION

    @ColumnInfo(name = "deep_link_url")
    val deepLinkUrl: String? = null,

    @ColumnInfo(name = "audio_locales")
    val audioLocales: String = "",  // comma-joined, e.g. "it-IT,ja-JP"

    @ColumnInfo(name = "subtitle_locales")
    val subtitleLocales: String = "",

    @ColumnInfo(name = "languages_assumed")
    val languagesAssumed: Boolean = false
) {
    companion object {
        const val PLATFORM_CRUNCHYROLL = "crunchyroll"
        const val PLATFORM_ANIME_GENERATION = "anime_generation"
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/local/entities/SeriesPlatform.kt
git commit -m "feat(db): add SeriesPlatform entity for per-platform availability"
```

---

## Task 3: Add external-id columns, register entity, bump to v4 with migration

**Files:**
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/entities/Series.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/database/AnimeDatabase.kt`

- [ ] **Step 1: Add external id columns to `Series`**

In `app/src/main/java/com/maverde/crunchybadges/data/local/entities/Series.kt`, add these fields before `addedTimestamp`:
```kotlin
    @ColumnInfo(name = "mal_id")
    val malId: Int? = null,

    @ColumnInfo(name = "anilist_id")
    val anilistId: Int? = null,

    @ColumnInfo(name = "tmdb_id")
    val tmdbId: Int? = null,
```

- [ ] **Step 2: Update the database class (entity list, version, exportSchema, migration)**

Replace the `@Database(...)` annotation and the builder in `AnimeDatabase.kt`. New annotation:
```kotlin
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
```
Add the migration inside the `companion object` and register it in the builder:
```kotlin
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
```
In `databaseBuilder(...)`, add before `.build()`:
```kotlin
                    .addMigrations(MIGRATION_3_4)
```
Keep `.fallbackToDestructiveMigration()` as a backstop only; with `MIGRATION_3_4` registered Room uses the migration for v3→v4 and does **not** wipe data. Add the import `import com.maverde.crunchybadges.data.local.entities.SeriesPlatform` (already covered by the existing `entities.*` import).

- [ ] **Step 3: Build to confirm Room compiles the schema**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL; a schema file `app/schemas/com.maverde.crunchybadges.data.local.database.AnimeDatabase/4.json` is generated.

- [ ] **Step 4: Commit (include the generated schema files)**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/local/entities/Series.kt \
        app/src/main/java/com/maverde/crunchybadges/data/local/database/AnimeDatabase.kt \
        app/schemas
git commit -m "feat(db): bump schema to v4 with series_platforms + external ids migration"
```

---

## Task 4: Migration test (instrumented)

**Files:**
- Test: `app/src/androidTest/java/com/maverde/crunchybadges/MigrationTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/androidTest/java/com/maverde/crunchybadges/MigrationTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run test to verify it fails (before migration code) / passes (after)**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.maverde.crunchybadges.MigrationTest"`
Expected: PASS (Task 3 already added the migration). If it fails with "no such table: series_platforms", the migration SQL in Task 3 is wrong — fix and re-run.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/maverde/crunchybadges/MigrationTest.kt
git commit -m "test(db): verify v3->v4 migration creates and backfills series_platforms"
```

---

## Task 5: Platforms relation + helpers in `SeriesWithAllData`; DAO methods

**Files:**
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/entities/SeriesWithAllData.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/dao/AnimeDao.kt`

- [ ] **Step 1: Add the relation + helpers**

In `SeriesWithAllData.kt`, add a relation field after `maturityRatings`:
```kotlin
    ,
    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val platforms: List<SeriesPlatform> = emptyList()
```
And add helpers in the body:
```kotlin
    fun isOnCrunchyroll(): Boolean =
        platforms.any { it.platform == SeriesPlatform.PLATFORM_CRUNCHYROLL }

    fun isOnAnimeGeneration(): Boolean =
        platforms.any { it.platform == SeriesPlatform.PLATFORM_ANIME_GENERATION }

    fun animeGenerationDeepLink(): String? =
        platforms.firstOrNull { it.platform == SeriesPlatform.PLATFORM_ANIME_GENERATION }?.deepLinkUrl
```

- [ ] **Step 2: Add DAO methods for platform rows**

In `AnimeDao.kt`, add inside the interface:
```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlatform(platform: SeriesPlatform)

    @Query("DELETE FROM series_platforms WHERE series_id = :seriesId AND platform = :platform")
    suspend fun deletePlatform(seriesId: String, platform: String)

    @Query("SELECT series_id FROM series_platforms WHERE platform = :platform")
    suspend fun getSeriesIdsForPlatform(platform: String): List<String>

    @Query("DELETE FROM series WHERE id = :seriesId AND NOT EXISTS (SELECT 1 FROM series_platforms WHERE series_platforms.series_id = :seriesId)")
    suspend fun deleteSeriesIfNoPlatforms(seriesId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM series WHERE id = :seriesId)")
    suspend fun seriesExistsById(seriesId: String): Boolean
```

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/local/entities/SeriesWithAllData.kt \
        app/src/main/java/com/maverde/crunchybadges/data/local/dao/AnimeDao.kt
git commit -m "feat(db): expose platforms relation + platform DAO methods"
```

---

## Task 6: Crunchyroll ingestion writes a `crunchyroll` platform row

**Files:**
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/dao/AnimeDao.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/repository/AnimeRepository.kt`

So that newly scraped CR series (not just migrated ones) get a platform row.

- [ ] **Step 1: Extend `insertSeriesWithAllData` to upsert the CR platform row**

In `AnimeDao.kt`, at the end of the `insertSeriesWithAllData` transaction body (after the maturityRatings insert), add:
```kotlin
        // Ensure a Crunchyroll platform row exists for this series.
        insertPlatform(
            com.maverde.crunchybadges.data.local.entities.SeriesPlatform(
                seriesId = data.series.id,
                platform = com.maverde.crunchybadges.data.local.entities.SeriesPlatform.PLATFORM_CRUNCHYROLL
            )
        )
```

- [ ] **Step 2: Build**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/local/dao/AnimeDao.kt
git commit -m "feat(db): record crunchyroll platform row on CR series insert"
```

---

## Task 7: Anime Generation JSON models + parse test (JVM)

**Files:**
- Create: `app/src/main/java/com/maverde/crunchybadges/data/models/AnimeGenerationCatalog.kt`
- Test: `app/src/test/java/com/maverde/crunchybadges/AnimeGenerationCatalogParseTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/maverde/crunchybadges/AnimeGenerationCatalogParseTest.kt`:
```kotlin
package com.maverde.crunchybadges

import com.maverde.crunchybadges.data.models.AnimeGenerationCatalog
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimeGenerationCatalogParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parsesContractJson() {
        val text = """
        {
          "version": 1,
          "generated_at": "2026-06-07T12:00:00Z",
          "titles": [
            {
              "ag_id": "ts1", "title": "Lamù", "year": 1981,
              "matched_crunchyroll_id": null,
              "external_ids": {"mal_id": 1, "anilist_id": 290, "tmdb_id": 26209},
              "description_it": "Desc IT", "poster_tall": "https://t.jpg",
              "poster_wide": "https://w.jpg", "genres": ["Commedia"], "rating": 7.8,
              "audio_locales": ["it-IT","ja-JP"], "subtitle_locales": ["it-IT"],
              "languages_assumed": false, "deep_link_url": "https://pv/lamu"
            }
          ]
        }
        """.trimIndent()

        val catalog = json.decodeFromString(AnimeGenerationCatalog.serializer(), text)

        assertEquals(1, catalog.version)
        assertEquals(1, catalog.titles.size)
        val t = catalog.titles[0]
        assertEquals("ts1", t.ag_id)
        assertNull(t.matched_crunchyroll_id)
        assertEquals(26209, t.external_ids.tmdb_id)
        assertEquals(listOf("it-IT", "ja-JP"), t.audio_locales)
        assertEquals(false, t.languages_assumed)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*AnimeGenerationCatalogParseTest"`
Expected: FAIL (unresolved reference `AnimeGenerationCatalog`).

- [ ] **Step 3: Create the models**

`app/src/main/java/com/maverde/crunchybadges/data/models/AnimeGenerationCatalog.kt`:
```kotlin
package com.maverde.crunchybadges.data.models

import kotlinx.serialization.Serializable

@Serializable
data class AnimeGenerationCatalog(
    val version: Int,
    val generated_at: String,
    val titles: List<AnimeGenerationTitle> = emptyList()
)

@Serializable
data class AnimeGenerationTitle(
    val ag_id: String,
    val title: String,
    val year: Int? = null,
    val matched_crunchyroll_id: String? = null,
    val external_ids: AnimeGenerationExternalIds = AnimeGenerationExternalIds(),
    val description_it: String = "",
    val poster_tall: String = "",
    val poster_wide: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val audio_locales: List<String> = emptyList(),
    val subtitle_locales: List<String> = emptyList(),
    val languages_assumed: Boolean = false,
    val deep_link_url: String = ""
)

@Serializable
data class AnimeGenerationExternalIds(
    val mal_id: Int? = null,
    val anilist_id: Int? = null,
    val tmdb_id: Int? = null
)
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*AnimeGenerationCatalogParseTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/models/AnimeGenerationCatalog.kt \
        app/src/test/java/com/maverde/crunchybadges/AnimeGenerationCatalogParseTest.kt
git commit -m "feat(sync): add Anime Generation catalog JSON models + parse test"
```

---

## Task 8: Repository ingestion/merge + instrumented test

**Files:**
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/repository/AnimeRepository.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/local/dao/AnimeDao.kt` (add upsert for AG-only base rows)
- Test: `app/src/androidTest/java/com/maverde/crunchybadges/AnimeGenerationIngestTest.kt`

- [ ] **Step 1: Add the ingestion method to the repository**

In `AnimeRepository.kt`, add the imports and method:
```kotlin
import com.maverde.crunchybadges.data.local.entities.SeriesPlatform
import com.maverde.crunchybadges.data.models.AnimeGenerationCatalog
import com.maverde.crunchybadges.data.models.AnimeGenerationTitle
```
```kotlin
    /**
     * Merge an Anime Generation catalog into the local DB.
     * - matched_crunchyroll_id present and series exists -> attach AG platform row
     * - otherwise -> upsert an "AG-only" series (id "ag:<ag_id>") + AG platform row
     * - prune AG platform rows for titles no longer present; drop AG-only series
     *   that end up with zero platform rows.
     */
    suspend fun ingestAnimeGeneration(catalog: AnimeGenerationCatalog) {
        val incomingSeriesIds = mutableSetOf<String>()

        for (t in catalog.titles) {
            val seriesId = resolveSeriesId(t)
            incomingSeriesIds.add(seriesId)

            // AG-only title needs a base Series + metadata/rating/images/locales.
            if (seriesId.startsWith(AG_ID_PREFIX)) {
                animeDao.insertSeriesWithAllData(t.toSeriesWithAllData(seriesId))
                // insertSeriesWithAllData also writes a crunchyroll row; remove it
                // because this is an AG-only series.
                animeDao.deletePlatform(seriesId, SeriesPlatform.PLATFORM_CRUNCHYROLL)
            } else {
                // Matched to an existing CR series: backfill external ids only.
                backfillExternalIds(seriesId, t)
            }

            animeDao.insertPlatform(
                SeriesPlatform(
                    seriesId = seriesId,
                    platform = SeriesPlatform.PLATFORM_ANIME_GENERATION,
                    deepLinkUrl = t.deep_link_url.ifEmpty { null },
                    audioLocales = t.audio_locales.joinToString(","),
                    subtitleLocales = t.subtitle_locales.joinToString(","),
                    languagesAssumed = t.languages_assumed
                )
            )
        }

        // Prune AG rows that disappeared from the catalog.
        val existingAg = animeDao.getSeriesIdsForPlatform(SeriesPlatform.PLATFORM_ANIME_GENERATION)
        for (id in existingAg) {
            if (id !in incomingSeriesIds) {
                animeDao.deletePlatform(id, SeriesPlatform.PLATFORM_ANIME_GENERATION)
                if (id.startsWith(AG_ID_PREFIX)) {
                    animeDao.deleteSeriesIfNoPlatforms(id)
                }
            }
        }
    }

    private suspend fun resolveSeriesId(t: AnimeGenerationTitle): String {
        val crId = t.matched_crunchyroll_id
        if (crId != null && animeDao.seriesExistsById(crId)) return crId
        return AG_ID_PREFIX + t.ag_id
    }

    private suspend fun backfillExternalIds(seriesId: String, t: AnimeGenerationTitle) {
        val existing = animeDao.getSeriesById(seriesId)?.series ?: return
        animeDao.insertSeries(
            existing.copy(
                malId = t.external_ids.mal_id ?: existing.malId,
                anilistId = t.external_ids.anilist_id ?: existing.anilistId,
                tmdbId = t.external_ids.tmdb_id ?: existing.tmdbId
            )
        )
    }

    companion object {
        const val AG_ID_PREFIX = "ag:"
    }
```

- [ ] **Step 2: Add the AG-only mapper to the repository**

Still in `AnimeRepository.kt`, add a private mapper that builds `SeriesWithAllData` from an `AnimeGenerationTitle`. It populates the shared `series_audio_locales`/`series_subtitle_locales` so the existing language badge + audio filter work uniformly:
```kotlin
    private fun AnimeGenerationTitle.toSeriesWithAllData(seriesId: String): SeriesWithAllData {
        val now = System.currentTimeMillis()
        val series = Series(
            id = seriesId,
            title = this.title,
            description = this.description_it,
            type = "series",
            malId = this.external_ids.mal_id,
            anilistId = this.external_ids.anilist_id,
            tmdbId = this.external_ids.tmdb_id,
            addedTimestamp = now,
            updatedTimestamp = now
        )
        val metadata = SeriesMetadata(
            seriesId = seriesId,
            episodeCount = 0,
            seasonCount = 0,
            seriesLaunchYear = this.year,
            isDubbed = this.audio_locales.any { it.startsWith("it") },
            isSubbed = this.subtitle_locales.isNotEmpty(),
            isMature = false,
            isSimulcast = false,
            matureBlocked = false,
            availabilityNotes = null,
            extendedDescription = null,
            extMaturityLevel = null,
            extMaturityRating = null,
            extMaturitySystem = null
        )
        val rating = this.rating?.let {
            Rating(seriesId = seriesId, average = it, total = 0, rating = null)
        }
        val images = buildList {
            if (poster_tall.isNotEmpty())
                add(Image(seriesId = seriesId, type = "poster_tall", sourceUrl = poster_tall, height = 720, width = 480))
            if (poster_wide.isNotEmpty())
                add(Image(seriesId = seriesId, type = "poster_wide", sourceUrl = poster_wide, height = 1080, width = 1920))
        }
        return SeriesWithAllData(
            series = series,
            metadata = metadata,
            rating = rating,
            images = images,
            awards = emptyList(),
            audioLocales = this.audio_locales.map { AudioLocale(seriesId, it) },
            subtitleLocales = this.subtitle_locales.map { SubtitleLocale(seriesId, it) },
            contentDescriptors = emptyList(),
            maturityRatings = emptyList(),
            platforms = emptyList()
        )
    }
```
Confirm `Rating`'s constructor allows the breakdown fields to default (it does — they are nullable/zero-defaulted in the entity). If any breakdown field is non-optional, pass the same defaults used in the CR mapper.

- [ ] **Step 3: Write the failing instrumented test**

`app/src/androidTest/java/com/maverde/crunchybadges/AnimeGenerationIngestTest.kt`:
```kotlin
package com.maverde.crunchybadges

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.local.entities.*
import com.maverde.crunchybadges.data.models.*
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeGenerationIngestTest {

    private lateinit var db: AnimeDatabase
    private lateinit var repo: AnimeRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AnimeDatabase::class.java).build()
        repo = AnimeRepository(db.animeDao(), ctx)
    }

    @After fun teardown() = db.close()

    private fun agTitle(agId: String, crId: String?, title: String) = AnimeGenerationTitle(
        ag_id = agId, title = title, year = 2000, matched_crunchyroll_id = crId,
        external_ids = AnimeGenerationExternalIds(mal_id = 5),
        description_it = "d", poster_tall = "t", poster_wide = "w",
        genres = listOf("Action"), rating = 7.0,
        audio_locales = listOf("it-IT"), subtitle_locales = listOf("it-IT"),
        languages_assumed = false, deep_link_url = "https://pv/$agId"
    )

    @Test fun matchedTitleAttachesAgPlatformToExistingSeries() = runBlocking {
        db.animeDao().insertSeries(Series(id = "GR1", title = "Naruto", description = "d", type = "series"))
        repo.ingestAnimeGeneration(AnimeGenerationCatalog(1, "now", listOf(agTitle("a1", "GR1", "Naruto"))))

        val s = db.animeDao().getSeriesById("GR1")!!
        assertTrue(s.isOnAnimeGeneration())
        assertEquals("https://pv/a1", s.animeGenerationDeepLink())
        // no duplicate AG-only series created
        assertNull(db.animeDao().getSeriesById("ag:a1"))
    }

    @Test fun unmatchedTitleCreatesAgOnlySeries() = runBlocking {
        repo.ingestAnimeGeneration(AnimeGenerationCatalog(1, "now", listOf(agTitle("a2", null, "Lamù"))))

        val s = db.animeDao().getSeriesById("ag:a2")!!
        assertEquals("Lamù", s.series.title)
        assertTrue(s.isOnAnimeGeneration())
        assertFalse(s.isOnCrunchyroll())
        assertTrue(s.hasAudioLocale("it-IT"))   // shared table populated -> badge works
    }

    @Test fun removedTitlePrunesAgOnlySeries() = runBlocking {
        repo.ingestAnimeGeneration(AnimeGenerationCatalog(1, "now", listOf(agTitle("a3", null, "Gone"))))
        assertNotNull(db.animeDao().getSeriesById("ag:a3"))
        // next sync no longer contains a3
        repo.ingestAnimeGeneration(AnimeGenerationCatalog(1, "now", emptyList()))
        assertNull(db.animeDao().getSeriesById("ag:a3"))
    }
}
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.maverde.crunchybadges.AnimeGenerationIngestTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/repository/AnimeRepository.kt \
        app/src/androidTest/java/com/maverde/crunchybadges/AnimeGenerationIngestTest.kt
git commit -m "feat(sync): merge Anime Generation catalog into DB with prune"
```

---

## Task 9: `AnimeGenerationSyncWorker` + test

**Files:**
- Create: `app/src/main/java/com/maverde/crunchybadges/sync/AnimeGenerationSyncWorker.kt`
- Test: `app/src/androidTest/java/com/maverde/crunchybadges/AnimeGenerationSyncWorkerTest.kt`

- [ ] **Step 1: Create the worker**

`app/src/main/java/com/maverde/crunchybadges/sync/AnimeGenerationSyncWorker.kt`:
```kotlin
package com.maverde.crunchybadges.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.models.AnimeGenerationCatalog
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads the published Anime Generation catalog JSON and merges it into the DB.
 * Scheduled periodically by WorkManager; also enqueued once on app launch.
 */
class AnimeGenerationSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(CATALOG_URL).build()
            val body = client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.retry()
                resp.body?.string() ?: return@withContext Result.retry()
            }
            val catalog = json.decodeFromString(AnimeGenerationCatalog.serializer(), body)
            val db = AnimeDatabase.getDatabase(applicationContext)
            val repo = AnimeRepository(db.animeDao(), applicationContext)
            repo.ingestAnimeGeneration(catalog)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("AGSyncWorker", "sync failed", e)
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "anime_generation_sync"
        // TODO at integration time: replace with the real published URL
        // (GitHub Pages / raw) from the pipeline plan.
        const val CATALOG_URL =
            "https://maverde73.github.io/crunchyroll-android-badges/catalog/catalog_anime_generation.json"
    }
}
```

- [ ] **Step 2: Write the failing test (worker parses + ingests from a stubbed HTTP)**

Because the worker builds its own OkHttp client, the test points `CATALOG_URL` at a local `MockWebServer`. Add the test dependency first: in `app/build.gradle.kts` add `androidTestImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")`, then:

`app/src/androidTest/java/com/maverde/crunchybadges/AnimeGenerationSyncWorkerTest.kt`:
```kotlin
package com.maverde.crunchybadges

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.sync.AnimeGenerationSyncWorker
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnimeGenerationSyncWorkerTest {

    @Test fun workerDownloadsAndIngests() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse().setBody(
            """{"version":1,"generated_at":"now","titles":[
                {"ag_id":"a1","title":"Lamù","matched_crunchyroll_id":null,
                 "audio_locales":["it-IT"],"subtitle_locales":["it-IT"],
                 "deep_link_url":"https://pv/a1"}]}"""
        ))
        server.start()

        // Inject the mock URL via a subclass override of CATALOG_URL is not possible
        // (const). Instead the worker reads inputData "url" when present:
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val worker = TestListenableWorkerBuilder<AnimeGenerationSyncWorker>(ctx)
            .setInputData(androidx.work.workDataOf("url" to server.url("/c.json").toString()))
            .build()

        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val db = AnimeDatabase.getDatabase(ctx)
        assertTrue(db.animeDao().getSeriesById("ag:a1")!!.isOnAnimeGeneration())
        server.shutdown()
    }
}
```

- [ ] **Step 3: Make `CATALOG_URL` overridable via inputData (minimal change for testability)**

In `AnimeGenerationSyncWorker.doWork()`, replace the `url(CATALOG_URL)` line with:
```kotlin
            val url = inputData.getString("url") ?: CATALOG_URL
            val request = Request.Builder().url(url).build()
```

- [ ] **Step 4: Run the test**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.maverde.crunchybadges.AnimeGenerationSyncWorkerTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/com/maverde/crunchybadges/sync/AnimeGenerationSyncWorker.kt \
        app/src/androidTest/java/com/maverde/crunchybadges/AnimeGenerationSyncWorkerTest.kt
git commit -m "feat(sync): add AnimeGenerationSyncWorker downloading + ingesting catalog"
```

---

## Task 10: Schedule the worker (periodic + on launch)

**Files:**
- Create: `app/src/main/java/com/maverde/crunchybadges/sync/AnimeGenerationSyncScheduler.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/ui/splash/SplashViewModel.kt`

- [ ] **Step 1: Create the scheduler helper**

`app/src/main/java/com/maverde/crunchybadges/sync/AnimeGenerationSyncScheduler.kt`:
```kotlin
package com.maverde.crunchybadges.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AnimeGenerationSyncScheduler {

    private val networkConstraint = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /** Enqueue a one-shot sync now (called on app launch). */
    fun syncNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<AnimeGenerationSyncWorker>()
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AnimeGenerationSyncWorker.UNIQUE_WORK_NAME + "_now",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Ensure the periodic (~12h) sync is scheduled. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<AnimeGenerationSyncWorker>(12, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            AnimeGenerationSyncWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
```

- [ ] **Step 2: Trigger from `SplashViewModel.startScraping()`**

In `SplashViewModel.kt`, at the start of the `viewModelScope.launch { ... }` block in `startScraping()` (before computing `syncMode`), add:
```kotlin
            com.maverde.crunchybadges.sync.AnimeGenerationSyncScheduler.syncNow(getApplication())
            com.maverde.crunchybadges.sync.AnimeGenerationSyncScheduler.schedulePeriodic(getApplication())
```
This kicks off the AG download in parallel with the Crunchyroll scrape; the worker ingests independently and the UI Flow updates when rows land.

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/sync/AnimeGenerationSyncScheduler.kt \
        app/src/main/java/com/maverde/crunchybadges/ui/splash/SplashViewModel.kt
git commit -m "feat(sync): schedule AG sync on launch + periodic 12h"
```

---

## Task 11: Platform filter — FilterState + persistence (JVM test)

**Files:**
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/models/FilterState.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/preferences/FilterPreferences.kt`
- Test: `app/src/test/java/com/maverde/crunchybadges/FilterStateTest.kt`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/com/maverde/crunchybadges/FilterStateTest.kt`:
```kotlin
package com.maverde.crunchybadges

import com.maverde.crunchybadges.data.models.FilterState
import com.maverde.crunchybadges.data.models.PlatformFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterStateTest {

    @Test fun defaultPlatformIsAll_andNotActive() {
        val f = FilterState()
        assertEquals(PlatformFilter.ALL, f.platform)
        assertFalse(f.hasActiveFilters())
    }

    @Test fun platformOtherThanAllIsActive() {
        val f = FilterState(platform = PlatformFilter.ANIME_GENERATION)
        assertTrue(f.hasActiveFilters())
    }

    @Test fun resetKeepsSortDropsPlatform() {
        val f = FilterState(platform = PlatformFilter.CRUNCHYROLL).reset()
        assertEquals(PlatformFilter.ALL, f.platform)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "*FilterStateTest"`
Expected: FAIL (unresolved `PlatformFilter` / `platform`).

- [ ] **Step 3: Extend `FilterState`**

In `FilterState.kt`, add the enum and field. Add to the data class (after `isSubbed`):
```kotlin
    val platform: PlatformFilter = PlatformFilter.ALL,
```
Add `platform != PlatformFilter.ALL` to the `hasActiveFilters()` return expression:
```kotlin
                isSubbed != null ||
                platform != PlatformFilter.ALL
```
Add `platform = PlatformFilter.ALL` to the `reset()` (it already resets by constructing fresh `FilterState(sortBy = sortBy)`, so `platform` defaults to ALL — no change needed, but keep the test green). Add the enum at file end:
```kotlin
enum class PlatformFilter(val displayName: String) {
    ALL("Tutte le piattaforme"),
    CRUNCHYROLL("Crunchyroll"),
    ANIME_GENERATION("Anime Generation")
}
```

- [ ] **Step 4: Persist in `FilterPreferences`**

In `FilterPreferences.kt`, in `saveFilter(...)` add `putString("platform", filter.platform.name)`, and in `loadFilter()` read it back:
```kotlin
            platform = com.maverde.crunchybadges.data.models.PlatformFilter.valueOf(
                prefs.getString("platform", com.maverde.crunchybadges.data.models.PlatformFilter.ALL.name)
                    ?: com.maverde.crunchybadges.data.models.PlatformFilter.ALL.name
            ),
```
(Match the existing key/editor style in that file.)

- [ ] **Step 5: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "*FilterStateTest"`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/models/FilterState.kt \
        app/src/main/java/com/maverde/crunchybadges/data/preferences/FilterPreferences.kt \
        app/src/test/java/com/maverde/crunchybadges/FilterStateTest.kt
git commit -m "feat(filter): add platform dimension to FilterState + persistence"
```

---

## Task 12: Platform filter in the dynamic query (instrumented test)

**Files:**
- Modify: `app/src/main/java/com/maverde/crunchybadges/data/repository/AnimeRepository.kt`
- Test: `app/src/androidTest/java/com/maverde/crunchybadges/PlatformFilterQueryTest.kt`

- [ ] **Step 1: Add the platform WHERE clause to `getSeriesFiltered`**

In `AnimeRepository.getSeriesFiltered(...)`, after the min-rating filter block and before the "Add WHERE clause" block, add:
```kotlin
        // Platform filter
        when (filter.platform) {
            com.maverde.crunchybadges.data.models.PlatformFilter.CRUNCHYROLL ->
                whereClauses.add("id IN (SELECT series_id FROM series_platforms WHERE platform = 'crunchyroll')")
            com.maverde.crunchybadges.data.models.PlatformFilter.ANIME_GENERATION ->
                whereClauses.add("id IN (SELECT series_id FROM series_platforms WHERE platform = 'anime_generation')")
            com.maverde.crunchybadges.data.models.PlatformFilter.ALL -> { /* no clause */ }
        }
```

- [ ] **Step 2: Write the failing instrumented test**

`app/src/androidTest/java/com/maverde/crunchybadges/PlatformFilterQueryTest.kt`:
```kotlin
package com.maverde.crunchybadges

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.local.entities.Series
import com.maverde.crunchybadges.data.local.entities.SeriesPlatform
import com.maverde.crunchybadges.data.models.FilterState
import com.maverde.crunchybadges.data.models.PlatformFilter
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformFilterQueryTest {

    private lateinit var db: AnimeDatabase
    private lateinit var repo: AnimeRepository

    @Before fun setup() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AnimeDatabase::class.java).build()
        repo = AnimeRepository(db.animeDao(), ctx)
    }

    @After fun teardown() = db.close()

    @Test fun filtersByAnimeGenerationPlatform() = runBlocking {
        val dao = db.animeDao()
        dao.insertSeries(Series(id = "GR1", title = "CR Only", description = "d", type = "series"))
        dao.insertPlatform(SeriesPlatform("GR1", SeriesPlatform.PLATFORM_CRUNCHYROLL))
        dao.insertSeries(Series(id = "ag:1", title = "AG Only", description = "d", type = "series"))
        dao.insertPlatform(SeriesPlatform("ag:1", SeriesPlatform.PLATFORM_ANIME_GENERATION))

        val agResults = repo.getSeriesFiltered(FilterState(platform = PlatformFilter.ANIME_GENERATION)).first()
        assertEquals(listOf("ag:1"), agResults.map { it.series.id })

        val crResults = repo.getSeriesFiltered(FilterState(platform = PlatformFilter.CRUNCHYROLL)).first()
        assertEquals(listOf("GR1"), crResults.map { it.series.id })
    }
}
```

- [ ] **Step 3: Run the test**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "com.maverde.crunchybadges.PlatformFilterQueryTest"`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/data/repository/AnimeRepository.kt \
        app/src/androidTest/java/com/maverde/crunchybadges/PlatformFilterQueryTest.kt
git commit -m "feat(filter): filter series list by platform in dynamic query"
```

---

## Task 13: Platform badges on the card

**Files:**
- Modify: `app/src/main/res/layout/item_anime_card.xml`
- Modify: `app/src/main/java/com/maverde/crunchybadges/ui/main/AnimeListAdapter.kt`

- [ ] **Step 1: Add a platform badge row to the layout**

In `item_anime_card.xml`, inside the existing top-level `FrameLayout`, after the language badge `TextView` (id `languageBadgeText`), add a horizontal container pinned bottom-start of the poster area:
```xml
    <!-- Platform badges (bottom-start) -->
    <LinearLayout
        android:id="@+id/platformBadges"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|start"
        android:layout_margin="8dp"
        android:orientation="horizontal"
        android:elevation="8dp">

        <TextView
            android:id="@+id/badgeCrunchyroll"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="CR"
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:textSize="11sp"
            android:paddingStart="6dp"
            android:paddingEnd="6dp"
            android:paddingTop="2dp"
            android:paddingBottom="2dp"
            android:background="#F47521"
            android:visibility="gone"
            tools:visibility="visible" />

        <TextView
            android:id="@+id/badgeAnimeGeneration"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="AG"
            android:textColor="#FFFFFF"
            android:textStyle="bold"
            android:textSize="11sp"
            android:layout_marginStart="4dp"
            android:paddingStart="6dp"
            android:paddingEnd="6dp"
            android:paddingTop="2dp"
            android:paddingBottom="2dp"
            android:background="#1AA3A3"
            android:visibility="gone"
            tools:visibility="visible" />
    </LinearLayout>
```

- [ ] **Step 2: Bind the badges in the adapter**

In `AnimeListAdapter.SeriesViewHolder`, add fields:
```kotlin
        private val badgeCrunchyroll: TextView = itemView.findViewById(R.id.badgeCrunchyroll)
        private val badgeAnimeGeneration: TextView = itemView.findViewById(R.id.badgeAnimeGeneration)
```
In `bind(seriesData)`, after the language-badge block, add:
```kotlin
            badgeCrunchyroll.visibility = if (seriesData.isOnCrunchyroll()) View.VISIBLE else View.GONE
            badgeAnimeGeneration.visibility = if (seriesData.isOnAnimeGeneration()) View.VISIBLE else View.GONE
```

- [ ] **Step 3: Build + visual verification**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. Then install on a Fire TV (`adb install -r app/build/outputs/apk/debug/app-debug.apk`) and confirm: CR titles show an orange `CR` badge, AG-only titles show a teal `AG` badge, dual-platform titles show both. (Requires a real or fixture catalog ingested; you can side-load a fixture JSON by pointing `CATALOG_URL` at a local file during manual testing.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/item_anime_card.xml \
        app/src/main/java/com/maverde/crunchybadges/ui/main/AnimeListAdapter.kt
git commit -m "feat(ui): show per-platform badges on series cards"
```

---

## Task 14: Platform control in the filter bottom sheet

**Files:**
- Modify: `app/src/main/res/layout/bottom_sheet_filters.xml`
- Modify: `app/src/main/java/com/maverde/crunchybadges/ui/filters/FilterBottomSheet.kt`

- [ ] **Step 1: Add a platform Spinner to the layout**

In `bottom_sheet_filters.xml`, add (near the existing audio-locale spinner; match its surrounding label/spinner pattern):
```xml
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Piattaforma"
        android:textColor="#FFFFFF"
        android:textStyle="bold"
        android:layout_marginTop="16dp" />

    <Spinner
        android:id="@+id/platformSpinner"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp" />
```

- [ ] **Step 2: Wire the spinner in `FilterBottomSheet`**

Following the existing `setupAudioLocaleSpinner()` pattern, add a `setupPlatformSpinner()` that fills the spinner with `PlatformFilter.values().map { it.displayName }`, preselects the current filter's platform, and on selection updates the working `FilterState` copy via `.copy(platform = PlatformFilter.values()[position])`. Call it from `onViewCreated`. When the sheet applies (`applyFilter()`), the platform is already part of the emitted `FilterState`, so `MainViewModel.updateFilter` persists and reloads with no further change.

Concrete spinner setup:
```kotlin
    private fun setupPlatformSpinner(view: View) {
        val spinner = view.findViewById<android.widget.Spinner>(R.id.platformSpinner)
        val options = com.maverde.crunchybadges.data.models.PlatformFilter.values()
        spinner.adapter = android.widget.ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            options.map { it.displayName }
        )
        spinner.setSelection(options.indexOf(currentFilter.platform))
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                currentFilter = currentFilter.copy(platform = options[pos])
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
    }
```
(Use the same `currentFilter` working-state variable name this file already uses for the other spinners; if it differs, match it.)

- [ ] **Step 3: Build + verify**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. Manually: open the filter sheet (Menu button on Fire TV), pick "Anime Generation", confirm the grid shows only AG titles.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/layout/bottom_sheet_filters.xml \
        app/src/main/java/com/maverde/crunchybadges/ui/filters/FilterBottomSheet.kt
git commit -m "feat(ui): add platform selector to the filter sheet"
```

---

## Task 15: "Open in Prime Video" deep link

**Files:**
- Modify: `app/src/main/java/com/maverde/crunchybadges/IntentLauncher.kt`
- Modify: `app/src/main/java/com/maverde/crunchybadges/ui/detail/DetailActivity.kt`

- [ ] **Step 1: Add a Prime Video launch method to `IntentLauncher`**

In `IntentLauncher.kt`, add:
```kotlin
    /**
     * Open an Anime Generation title in Prime Video via its web/deep-link URL.
     * No setPackage() so Fire OS AppsFilter does not block it (same approach as
     * the Crunchyroll Fire TV path).
     */
    fun launchPrimeVideo(deepLinkUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(deepLinkUrl)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            android.util.Log.d("CrunchyBadges", "Opened Prime Video link: $deepLinkUrl")
        } catch (e: Exception) {
            android.util.Log.e("CrunchyBadges", "Failed to open Prime Video link", e)
            Toast.makeText(activity, "Impossibile aprire Prime Video", Toast.LENGTH_LONG).show()
        }
    }
```

- [ ] **Step 2: Wire it into `DetailActivity`**

In `DetailActivity`, where the existing "open in Crunchyroll" action is set up (`openCrunchyrollPage()` / the open button), branch on availability of the loaded `SeriesWithAllData`:
- If `seriesData.isOnAnimeGeneration()` and `animeGenerationDeepLink()` is non-null, show/enable an "Apri in Prime Video" button that calls `IntentLauncher(this).launchPrimeVideo(link)`.
- Keep the Crunchyroll button enabled only when `seriesData.isOnCrunchyroll()`.

Minimal wiring (add to the method that binds the loaded series; reuse the existing open button or add a second one in `activity_detail.xml` with id `openPrimeVideoButton`):
```kotlin
        val agLink = seriesData.animeGenerationDeepLink()
        if (agLink != null) {
            openPrimeVideoButton.visibility = View.VISIBLE
            openPrimeVideoButton.setOnClickListener {
                IntentLauncher(this).launchPrimeVideo(agLink)
            }
        } else {
            openPrimeVideoButton.visibility = View.GONE
        }
```
If adding `openPrimeVideoButton`, add to `activity_detail.xml` a button mirroring the existing Crunchyroll button's style with `android:id="@+id/openPrimeVideoButton"` and `android:text="Apri in Prime Video"`, `android:visibility="gone"`.

- [ ] **Step 3: Build + verify**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL. Manually: open an AG-only title's detail → "Apri in Prime Video" launches the Prime Video app/page; an AG description already in Italian shows without the translate control (existing behavior: translate only triggers for non-Italian text).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/maverde/crunchybadges/IntentLauncher.kt \
        app/src/main/java/com/maverde/crunchybadges/ui/detail/DetailActivity.kt \
        app/src/main/res/layout/activity_detail.xml
git commit -m "feat(detail): add Open in Prime Video deep link for AG titles"
```

---

## Task 16: Full regression run

- [ ] **Step 1: JVM unit tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: all pass (parse + FilterState tests).

- [ ] **Step 2: Instrumented tests (device/emulator connected)**

Run: `./gradlew :app:connectedDebugAndroidTest`
Expected: MigrationTest, AnimeGenerationIngestTest, AnimeGenerationSyncWorkerTest, PlatformFilterQueryTest all pass.

- [ ] **Step 3: Assemble**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit any final fixups** (only if needed)

---

## Notes for the implementer

- **Translation feature for AG titles:** AG descriptions arrive already in Italian (TMDB it-IT). The existing `DetailActivity` translate flow targets non-Italian descriptions; leave it as-is so it is a no-op for AG titles. Do not delete it (CR descriptions still need it).
- **Per-platform vs shared locales:** ingestion writes AG locales into both `series_platforms` (per-platform, for the detail view) and the shared `series_audio_locales`/`series_subtitle_locales` (so the existing language-badge + audio filter work uniformly). This is intentional duplication, documented in the spec.
- **CATALOG_URL:** replace the placeholder GitHub Pages URL in `AnimeGenerationSyncWorker` with the real published path from the pipeline plan once it is live. The worker already supports an `inputData["url"]` override used by the test.
- **Real catalog dependency:** the app is fully testable with fixture JSON before the pipeline ships; for an end-to-end check, point `CATALOG_URL` at the pipeline's published file.
