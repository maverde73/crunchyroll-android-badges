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
