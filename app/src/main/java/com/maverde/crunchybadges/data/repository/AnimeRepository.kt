package com.maverde.crunchybadges.data.repository

import android.content.Context
import com.maverde.crunchybadges.data.local.dao.AnimeDao
import com.maverde.crunchybadges.data.local.entities.*
import com.maverde.crunchybadges.data.local.entities.TranslatedDescription
import com.maverde.crunchybadges.data.models.AnimeData
import com.maverde.crunchybadges.data.models.AnimeGenerationCatalog
import com.maverde.crunchybadges.data.models.AnimeGenerationTitle
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Repository for anime data operations
 * Handles data mapping between API models and database entities
 * Version 2: Updated to use normalized schema with SeriesWithAllData
 * Version 3: Added translation cache methods
 */
class AnimeRepository(
    private val animeDao: AnimeDao,
    private val context: Context
) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true  // For debug readability
    }

    /**
     * Get all series as Flow (auto-updates)
     */
    fun getAllSeries(): Flow<List<SeriesWithAllData>> {
        return animeDao.getAllSeriesFlow()
    }

    /**
     * Get series by ID
     */
    suspend fun getSeriesById(id: String): SeriesWithAllData? {
        return animeDao.getSeriesById(id)
    }

    /**
     * Check if a series exists in the database
     * Used for incremental sync to avoid re-downloading existing series
     */
    suspend fun seriesExists(id: String): Boolean {
        return animeDao.getSeriesById(id) != null
    }

    /**
     * Insert anime batch from API response
     * Only inserts series (items with series_metadata), skips movies and other content
     */
    suspend fun insertAnime(animeList: List<AnimeData>) {
        val seriesDataList = animeList
            .filter { it.series_metadata != null }  // Only series, not movies
            .map { it.toSeriesWithAllData() }

        animeDao.insertSeriesBatch(seriesDataList)

        // Save Italian anime to JSON file for debugging
        saveItalianAnimeToFile(animeList)
    }

    /**
     * Save Italian anime JSON to file for debugging
     * Accumulates across multiple batches instead of overwriting
     */
    private fun saveItalianAnimeToFile(animeList: List<AnimeData>) {
        try {
            val italianAnime = animeList.filter { anime ->
                anime.series_metadata?.audio_locales?.contains("it-IT") == true
            }

            if (italianAnime.isNotEmpty()) {
                val debugFile = File(context.filesDir, "italian_anime_debug.json")

                // Read existing data if file exists
                val existingAnime = if (debugFile.exists()) {
                    try {
                        val existingJson = debugFile.readText()
                        json.decodeFromString<List<AnimeData>>(existingJson)
                    } catch (e: Exception) {
                        android.util.Log.w("AnimeRepository", "Could not read existing file, starting fresh")
                        emptyList()
                    }
                } else {
                    emptyList()
                }

                // Combine existing + new (using ID to deduplicate)
                val allAnime = (existingAnime + italianAnime)
                    .distinctBy { it.id }
                    .sortedBy { it.title }

                // Save combined list
                val jsonContent = json.encodeToString(allAnime)
                debugFile.writeText(jsonContent)

                android.util.Log.d("AnimeRepository", "✅ Saved ${italianAnime.size} new Italian anime (total: ${allAnime.size}) to: ${debugFile.absolutePath}")

                // Sample the first anime for debugging
                if (italianAnime.isNotEmpty()) {
                    android.util.Log.d("AnimeRepository", "Sample - Title: ${italianAnime.first().title}")
                    android.util.Log.d("AnimeRepository", "  Rating: ${italianAnime.first().rating?.average ?: "null"}")
                    android.util.Log.d("AnimeRepository", "  Maturity: ${italianAnime.first().series_metadata?.maturity_ratings?.joinToString() ?: "null"}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AnimeRepository", "Failed to save debug JSON", e)
        }
    }

    /**
     * Get count of series with Italian audio
     */
    suspend fun getItalianSeriesCount(): Int {
        return animeDao.getSeriesByAudioLocales(listOf("it-IT")).size
    }

    /**
     * Get total series count
     */
    suspend fun getTotalSeriesCount(): Int {
        return animeDao.getSeriesCount()
    }

    /**
     * Search series by title or description
     */
    suspend fun searchByTitle(query: String): List<SeriesWithAllData> {
        return animeDao.searchSeries(query)
    }

    /**
     * Delete all series
     */
    suspend fun clearDatabase() {
        animeDao.deleteAllSeries()
    }

    /**
     * Get series with filters applied
     */
    fun getSeriesFiltered(filter: com.maverde.crunchybadges.data.models.FilterState): Flow<List<SeriesWithAllData>> {
        // Build dynamic query based on filter
        val queryBuilder = StringBuilder("SELECT * FROM series")
        val whereClauses = mutableListOf<String>()
        val args = mutableListOf<Any>()

        // Audio locale filter
        filter.audioLocale?.let { locale ->
            whereClauses.add("id IN (SELECT series_id FROM series_audio_locales WHERE locale_code = ?)")
            args.add(locale)
        }

        // Min rating filter
        if (filter.minRating > 0) {
            whereClauses.add("id IN (SELECT series_id FROM rating WHERE average >= ?)")
            args.add(filter.minRating)
        }

        // Platform filter
        when (filter.platform) {
            com.maverde.crunchybadges.data.models.PlatformFilter.CRUNCHYROLL ->
                whereClauses.add("id IN (SELECT series_id FROM series_platforms WHERE platform = 'crunchyroll')")
            com.maverde.crunchybadges.data.models.PlatformFilter.ANIME_GENERATION ->
                whereClauses.add("id IN (SELECT series_id FROM series_platforms WHERE platform = 'anime_generation')")
            com.maverde.crunchybadges.data.models.PlatformFilter.ALL -> { /* no clause */ }
        }

        // Add WHERE clause if any filters
        if (whereClauses.isNotEmpty()) {
            queryBuilder.append(" WHERE ").append(whereClauses.joinToString(" AND "))
        }

        // Add ORDER BY
        when (filter.sortBy) {
            com.maverde.crunchybadges.data.models.SortOption.TITLE_ASC,
            com.maverde.crunchybadges.data.models.SortOption.TITLE_DESC -> {
                queryBuilder.append(" ORDER BY title ${filter.sortBy.sqlDirection}")
            }
            com.maverde.crunchybadges.data.models.SortOption.RATING_DESC,
            com.maverde.crunchybadges.data.models.SortOption.RATING_ASC -> {
                queryBuilder.append(" ORDER BY (SELECT average FROM rating WHERE rating.series_id = series.id) ${filter.sortBy.sqlDirection}")
            }
            com.maverde.crunchybadges.data.models.SortOption.MATURITY_ASC,
            com.maverde.crunchybadges.data.models.SortOption.MATURITY_DESC -> {
                queryBuilder.append(" ORDER BY (SELECT ext_maturity_rating FROM series_metadata WHERE series_metadata.series_id = series.id) ${filter.sortBy.sqlDirection}")
            }
            com.maverde.crunchybadges.data.models.SortOption.ADDED_DESC,
            com.maverde.crunchybadges.data.models.SortOption.ADDED_ASC -> {
                queryBuilder.append(" ORDER BY added_timestamp ${filter.sortBy.sqlDirection}")
            }
            com.maverde.crunchybadges.data.models.SortOption.EPISODES_DESC,
            com.maverde.crunchybadges.data.models.SortOption.EPISODES_ASC -> {
                queryBuilder.append(" ORDER BY (SELECT episode_count FROM series_metadata WHERE series_metadata.series_id = series.id) ${filter.sortBy.sqlDirection}")
            }
            com.maverde.crunchybadges.data.models.SortOption.YEAR_DESC,
            com.maverde.crunchybadges.data.models.SortOption.YEAR_ASC -> {
                queryBuilder.append(" ORDER BY (SELECT series_launch_year FROM series_metadata WHERE series_metadata.series_id = series.id) ${filter.sortBy.sqlDirection}")
            }
        }

        val query = androidx.sqlite.db.SimpleSQLiteQuery(queryBuilder.toString(), args.toTypedArray())
        return animeDao.getSeriesFilteredRaw(query)
    }

    /**
     * Convert AnimeData (API model) to SeriesWithAllData (database entities)
     * Assumes series_metadata is not null (filtered before calling this)
     */
    private fun AnimeData.toSeriesWithAllData(): SeriesWithAllData {
        val metadata = this.series_metadata!!  // Safe because we filter before calling
        val currentTime = System.currentTimeMillis()

        // 1. Create main Series entity
        val series = Series(
            id = this.id,
            title = this.title,
            slugTitle = this.slug_title,
            description = this.description,
            type = this.type,
            isNew = this.new,
            lastPublic = this.last_public,
            channelId = this.channel_id,
            externalId = this.external_id,
            linkedResourceKey = this.linked_resource_key,
            promoDescription = this.promo_description,
            promoTitle = this.promo_title,
            slug = this.slug,
            addedTimestamp = currentTime,
            updatedTimestamp = currentTime
        )

        // 2. Create SeriesMetadata entity (1-to-1)
        val seriesMetadata = SeriesMetadata(
            seriesId = this.id,
            episodeCount = metadata.episode_count,
            seasonCount = metadata.season_count,
            seriesLaunchYear = metadata.series_launch_year,
            isDubbed = metadata.is_dubbed,
            isSubbed = metadata.is_subbed,
            isMature = metadata.is_mature,
            isSimulcast = metadata.is_simulcast,
            matureBlocked = metadata.mature_blocked,
            availabilityNotes = metadata.availability_notes,
            extendedDescription = metadata.extended_description,
            extMaturityLevel = metadata.extended_maturity_rating?.level,
            extMaturityRating = metadata.extended_maturity_rating?.rating,
            extMaturitySystem = metadata.extended_maturity_rating?.system
        )

        // 3. Create Rating entity (1-to-1) if rating data exists
        val ratingEntity = this.rating?.let { ratingInfo ->
            // Parse average rating
            val avgValue = ratingInfo.average.split("/").firstOrNull()?.trim()?.toDoubleOrNull() ?: 0.0

            Rating(
                seriesId = this.id,
                average = avgValue,
                total = ratingInfo.total,
                rating = ratingInfo.rating.takeIf { it.isNotEmpty() },
                // 1-star breakdown
                rating1sDisplayed = ratingInfo.`1s`?.displayed,
                rating1sPercentage = ratingInfo.`1s`?.percentage ?: 0,
                rating1sUnit = ratingInfo.`1s`?.unit,
                // 2-star breakdown
                rating2sDisplayed = ratingInfo.`2s`?.displayed,
                rating2sPercentage = ratingInfo.`2s`?.percentage ?: 0,
                rating2sUnit = ratingInfo.`2s`?.unit,
                // 3-star breakdown
                rating3sDisplayed = ratingInfo.`3s`?.displayed,
                rating3sPercentage = ratingInfo.`3s`?.percentage ?: 0,
                rating3sUnit = ratingInfo.`3s`?.unit,
                // 4-star breakdown
                rating4sDisplayed = ratingInfo.`4s`?.displayed,
                rating4sPercentage = ratingInfo.`4s`?.percentage ?: 0,
                rating4sUnit = ratingInfo.`4s`?.unit,
                // 5-star breakdown
                rating5sDisplayed = ratingInfo.`5s`?.displayed,
                rating5sPercentage = ratingInfo.`5s`?.percentage ?: 0,
                rating5sUnit = ratingInfo.`5s`?.unit
            )
        }

        // 4. Create Image entities (1-to-many)
        val images = mutableListOf<Image>()

        // Add all poster_tall variants
        this.images.poster_tall.flatten().forEach { variant ->
            images.add(
                Image(
                    seriesId = this.id,
                    type = "poster_tall",
                    sourceUrl = variant.source,
                    height = variant.height,
                    width = variant.width
                )
            )
        }

        // Add all poster_wide variants
        this.images.poster_wide.flatten().forEach { variant ->
            images.add(
                Image(
                    seriesId = this.id,
                    type = "poster_wide",
                    sourceUrl = variant.source,
                    height = variant.height,
                    width = variant.width
                )
            )
        }

        // 5. Create Award entities (1-to-many)
        val awards = this.awards.map { awardData ->
            Award(
                seriesId = this.id,
                text = awardData.text,
                iconUrl = awardData.icon_url,
                isWinner = awardData.is_winner,
                isCurrentAward = awardData.is_current_award
            )
        }

        // 6. Create AudioLocale entities (many-to-many)
        val audioLocales = metadata.audio_locales.map { localeCode ->
            AudioLocale(
                seriesId = this.id,
                localeCode = localeCode
            )
        }

        // 7. Create SubtitleLocale entities (many-to-many)
        val subtitleLocales = metadata.subtitle_locales.map { localeCode ->
            SubtitleLocale(
                seriesId = this.id,
                localeCode = localeCode
            )
        }

        // 8. Create ContentDescriptor entities (many-to-many)
        val contentDescriptors = metadata.content_descriptors.map { descriptor ->
            ContentDescriptor(
                seriesId = this.id,
                descriptor = descriptor
            )
        }

        // 9. Create MaturityRating entities (many-to-many)
        val maturityRatings = metadata.maturity_ratings.map { ratingCode ->
            MaturityRatingEntity(
                seriesId = this.id,
                ratingCode = ratingCode
            )
        }

        // Return the complete SeriesWithAllData object
        return SeriesWithAllData(
            series = series,
            metadata = seriesMetadata,
            rating = ratingEntity,
            images = images,
            awards = awards,
            audioLocales = audioLocales,
            subtitleLocales = subtitleLocales,
            contentDescriptors = contentDescriptors,
            maturityRatings = maturityRatings
        )
    }

    // ==================== TRANSLATION CACHE METHODS ====================

    /**
     * Get cached translation for a series
     */
    suspend fun getTranslation(seriesId: String): TranslatedDescription? {
        return animeDao.getTranslation(seriesId)
    }

    /**
     * Save translation to cache
     */
    suspend fun saveTranslation(translation: TranslatedDescription) {
        animeDao.insertTranslation(translation)
    }

    /**
     * Delete translation from cache
     */
    suspend fun deleteTranslation(seriesId: String) {
        animeDao.deleteTranslation(seriesId)
    }

    /**
     * Clear all cached translations
     */
    suspend fun clearAllTranslations() {
        animeDao.deleteAllTranslations()
    }

    // ==================== ANIME GENERATION INGESTION ====================

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

            if (seriesId.startsWith(AG_ID_PREFIX)) {
                // AG-only title needs a base Series + metadata/rating/images/locales.
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
            extMaturityRating = this.maturity_rating.ifEmpty { null }
        )
        val ratingEntity = this.rating?.let {
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
            rating = ratingEntity,
            images = images,
            awards = emptyList(),
            audioLocales = this.audio_locales.map { AudioLocale(seriesId, it) },
            subtitleLocales = this.subtitle_locales.map { SubtitleLocale(seriesId, it) },
            contentDescriptors = emptyList(),
            maturityRatings = emptyList(),
            platforms = emptyList()
        )
    }

    companion object {
        const val AG_ID_PREFIX = "ag:"
    }
}
