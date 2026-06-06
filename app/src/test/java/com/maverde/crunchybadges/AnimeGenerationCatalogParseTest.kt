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
