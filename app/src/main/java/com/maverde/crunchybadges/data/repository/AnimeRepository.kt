package com.maverde.crunchybadges.data.repository

import com.maverde.crunchybadges.data.local.dao.AnimeDao
import com.maverde.crunchybadges.data.local.entities.AnimeEntity
import com.maverde.crunchybadges.data.models.AnimeData
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository for anime data operations
 * Handles data mapping between API models and database entities
 */
class AnimeRepository(private val animeDao: AnimeDao) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get all Italian anime as Flow (auto-updates)
     */
    fun getAllItalianAnime(): Flow<List<AnimeEntity>> {
        return animeDao.getAllItalianAnimeFlow()
    }

    /**
     * Get anime by ID
     */
    suspend fun getAnimeById(id: String): AnimeEntity? {
        return animeDao.getAnimeById(id)
    }

    /**
     * Insert anime batch from API response
     */
    suspend fun insertAnime(animeList: List<AnimeData>) {
        val entities = animeList.map { it.toEntity() }
        animeDao.insertAll(entities)
    }

    /**
     * Get count of Italian anime
     */
    suspend fun getItalianAnimeCount(): Int {
        return animeDao.getItalianAnimeCount()
    }

    /**
     * Get total anime count
     */
    suspend fun getTotalAnimeCount(): Int {
        return animeDao.getTotalAnimeCount()
    }

    /**
     * Search anime by title
     */
    suspend fun searchByTitle(query: String): List<AnimeEntity> {
        return animeDao.searchByTitle(query)
    }

    /**
     * Delete all anime
     */
    suspend fun clearDatabase() {
        animeDao.deleteAll()
    }

    /**
     * Convert AnimeData (API model) to AnimeEntity (database)
     */
    private fun AnimeData.toEntity(): AnimeEntity {
        val posterTall = this.images.poster_tall.flatten()
            .find { it.width == 480 }?.source ?: ""
        val posterWide = this.images.poster_wide.flatten()
            .find { it.width == 1920 }?.source ?: ""

        return AnimeEntity(
            id = this.id,
            title = this.title,
            description = this.description,
            posterTallUrl = posterTall,
            posterWideUrl = posterWide,
            rating = this.rating?.average?.toDoubleOrNull() ?: 0.0,
            episodeCount = this.series_metadata.episode_count,
            seasonCount = this.series_metadata.season_count,
            audioLocalesJson = json.encodeToString(this.series_metadata.audio_locales),
            hasItalianAudio = this.series_metadata.audio_locales.contains("it-IT"),
            contentDescriptors = json.encodeToString(this.series_metadata.content_descriptors),
            maturityRating = this.series_metadata.maturity_ratings.firstOrNull() ?: "",
            addedTimestamp = System.currentTimeMillis(),
            updatedTimestamp = System.currentTimeMillis()
        )
    }
}
