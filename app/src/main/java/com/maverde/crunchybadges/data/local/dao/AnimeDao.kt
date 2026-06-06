package com.maverde.crunchybadges.data.local.dao

import androidx.room.*
import com.maverde.crunchybadges.data.local.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for anime database operations
 * Updated to support normalized schema with multiple tables
 */
@Dao
interface AnimeDao {

    // ==================== QUERY METHODS (with relations) ====================

    /**
     * Get all series with all related data as Flow (auto-updates UI)
     */
    @Transaction
    @Query("SELECT * FROM series ORDER BY title ASC")
    fun getAllSeriesFlow(): Flow<List<SeriesWithAllData>>

    /**
     * Get all series (one-shot)
     */
    @Transaction
    @Query("SELECT * FROM series ORDER BY title ASC")
    suspend fun getAllSeries(): List<SeriesWithAllData>

    /**
     * Get series by ID with all data
     */
    @Transaction
    @Query("SELECT * FROM series WHERE id = :seriesId")
    suspend fun getSeriesById(seriesId: String): SeriesWithAllData?

    /**
     * Get series filtered by audio locales
     */
    @Transaction
    @Query("""
        SELECT DISTINCT s.* FROM series s
        INNER JOIN series_audio_locales sal ON s.id = sal.series_id
        WHERE sal.locale_code IN (:locales)
        ORDER BY s.title ASC
    """)
    suspend fun getSeriesByAudioLocales(locales: List<String>): List<SeriesWithAllData>

    /**
     * Get series with minimum rating
     */
    @Transaction
    @Query("""
        SELECT s.* FROM series s
        INNER JOIN rating r ON s.id = r.series_id
        WHERE r.average >= :minRating
        ORDER BY r.average DESC
    """)
    suspend fun getSeriesByMinRating(minRating: Double): List<SeriesWithAllData>

    /**
     * Search series by title or description
     */
    @Transaction
    @Query("""
        SELECT * FROM series
        WHERE title LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
        ORDER BY title ASC
    """)
    suspend fun searchSeries(query: String): List<SeriesWithAllData>

    /**
     * Get total count of series
     */
    @Query("SELECT COUNT(*) FROM series")
    suspend fun getSeriesCount(): Int

    // ==================== INSERT METHODS ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(series: Series)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeriesList(series: List<Series>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: SeriesMetadata)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: Rating)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<Image>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAwards(awards: List<Award>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudioLocales(locales: List<AudioLocale>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubtitleLocales(locales: List<SubtitleLocale>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContentDescriptors(descriptors: List<ContentDescriptor>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMaturityRatings(ratings: List<MaturityRatingEntity>)

    /**
     * Insert a complete series with all its related data
     * This is a transaction that inserts into all tables
     */
    @Transaction
    suspend fun insertSeriesWithAllData(data: SeriesWithAllData) {
        // Insert main series
        insertSeries(data.series)

        // Insert metadata (1-to-1)
        data.metadata?.let { insertMetadata(it) }

        // Insert rating (1-to-1)
        data.rating?.let { insertRating(it) }

        // Delete existing related data to avoid duplicates
        deleteImagesForSeries(data.series.id)
        deleteAwardsForSeries(data.series.id)
        deleteAudioLocalesForSeries(data.series.id)
        deleteSubtitleLocalesForSeries(data.series.id)
        deleteContentDescriptorsForSeries(data.series.id)
        deleteMaturityRatingsForSeries(data.series.id)

        // Insert new related data (1-to-many and many-to-many)
        if (data.images.isNotEmpty()) insertImages(data.images)
        if (data.awards.isNotEmpty()) insertAwards(data.awards)
        if (data.audioLocales.isNotEmpty()) insertAudioLocales(data.audioLocales)
        if (data.subtitleLocales.isNotEmpty()) insertSubtitleLocales(data.subtitleLocales)
        if (data.contentDescriptors.isNotEmpty()) insertContentDescriptors(data.contentDescriptors)
        if (data.maturityRatings.isNotEmpty()) insertMaturityRatings(data.maturityRatings)

        // Ensure a Crunchyroll platform row exists for this series.
        insertPlatform(
            SeriesPlatform(
                seriesId = data.series.id,
                platform = SeriesPlatform.PLATFORM_CRUNCHYROLL
            )
        )
    }

    /**
     * Batch insert series with all data
     */
    @Transaction
    suspend fun insertSeriesBatch(dataList: List<SeriesWithAllData>) {
        dataList.forEach { insertSeriesWithAllData(it) }
    }

    // ==================== DELETE METHODS ====================

    @Query("DELETE FROM series")
    suspend fun deleteAllSeries()

    @Query("DELETE FROM image WHERE series_id = :seriesId")
    suspend fun deleteImagesForSeries(seriesId: String)

    @Query("DELETE FROM award WHERE series_id = :seriesId")
    suspend fun deleteAwardsForSeries(seriesId: String)

    @Query("DELETE FROM series_audio_locales WHERE series_id = :seriesId")
    suspend fun deleteAudioLocalesForSeries(seriesId: String)

    @Query("DELETE FROM series_subtitle_locales WHERE series_id = :seriesId")
    suspend fun deleteSubtitleLocalesForSeries(seriesId: String)

    @Query("DELETE FROM series_content_descriptors WHERE series_id = :seriesId")
    suspend fun deleteContentDescriptorsForSeries(seriesId: String)

    @Query("DELETE FROM series_maturity_ratings WHERE series_id = :seriesId")
    suspend fun deleteMaturityRatingsForSeries(seriesId: String)

    /**
     * Get series with filters applied
     * Constructs dynamic query based on filter parameters
     */
    @androidx.room.RawQuery(observedEntities = [Series::class])
    fun getSeriesFilteredRaw(query: androidx.sqlite.db.SupportSQLiteQuery): kotlinx.coroutines.flow.Flow<List<SeriesWithAllData>>

    // ==================== PLATFORM METHODS ====================

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

    // ==================== TRANSLATION CACHE METHODS ====================

    /**
     * Get cached translation for a series
     */
    @Query("SELECT * FROM translated_description WHERE series_id = :seriesId")
    suspend fun getTranslation(seriesId: String): TranslatedDescription?

    /**
     * Insert or update translation cache
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTranslation(translation: TranslatedDescription)

    /**
     * Delete translation for a series
     */
    @Query("DELETE FROM translated_description WHERE series_id = :seriesId")
    suspend fun deleteTranslation(seriesId: String)

    /**
     * Delete all cached translations
     */
    @Query("DELETE FROM translated_description")
    suspend fun deleteAllTranslations()
}
