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
