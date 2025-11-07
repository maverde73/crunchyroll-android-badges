package com.maverde.crunchybadges.data.local.dao

import androidx.room.*
import com.maverde.crunchybadges.data.local.entities.AnimeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for anime database operations
 */
@Dao
interface AnimeDao {

    /**
     * Get all Italian anime as Flow (auto-updates UI)
     */
    @Query("SELECT * FROM anime WHERE hasItalianAudio = 1 ORDER BY title ASC")
    fun getAllItalianAnimeFlow(): Flow<List<AnimeEntity>>

    /**
     * Get all Italian anime (one-shot)
     */
    @Query("SELECT * FROM anime WHERE hasItalianAudio = 1 ORDER BY title ASC")
    suspend fun getAllItalianAnime(): List<AnimeEntity>

    /**
     * Get anime by rating (descending)
     */
    @Query("SELECT * FROM anime WHERE hasItalianAudio = 1 AND rating >= :minRating ORDER BY rating DESC")
    suspend fun getAnimeByRating(minRating: Double): List<AnimeEntity>

    /**
     * Get anime by ID
     */
    @Query("SELECT * FROM anime WHERE id = :animeId")
    suspend fun getAnimeById(animeId: String): AnimeEntity?

    /**
     * Insert or replace anime (batch)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(anime: List<AnimeEntity>)

    /**
     * Insert or replace single anime
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(anime: AnimeEntity)

    /**
     * Get count of Italian anime
     */
    @Query("SELECT COUNT(*) FROM anime WHERE hasItalianAudio = 1")
    suspend fun getItalianAnimeCount(): Int

    /**
     * Get total count of all anime
     */
    @Query("SELECT COUNT(*) FROM anime")
    suspend fun getTotalAnimeCount(): Int

    /**
     * Delete all anime
     */
    @Query("DELETE FROM anime")
    suspend fun deleteAll()

    /**
     * Search anime by title
     */
    @Query("SELECT * FROM anime WHERE hasItalianAudio = 1 AND title LIKE '%' || :query || '%' ORDER BY title ASC")
    suspend fun searchByTitle(query: String): List<AnimeEntity>
}
