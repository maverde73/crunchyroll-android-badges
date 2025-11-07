package com.maverde.crunchybadges.data.models

import kotlinx.serialization.Serializable

/**
 * Data models for Crunchyroll API responses
 * These match the JSON structure from browse API
 */

@Serializable
data class BrowseResponse(
    val total: Int,
    val data: List<AnimeData>
)

@Serializable
data class AnimeData(
    val id: String,
    val title: String,
    val description: String,
    val images: Images,
    val series_metadata: SeriesMetadata,
    val rating: RatingInfo? = null
)

@Serializable
data class Images(
    val poster_tall: List<List<ImageVariant>>,
    val poster_wide: List<List<ImageVariant>>
)

@Serializable
data class ImageVariant(
    val source: String,
    val width: Int,
    val height: Int
)

@Serializable
data class SeriesMetadata(
    val audio_locales: List<String>,
    val subtitle_locales: List<String> = emptyList(),
    val episode_count: Int,
    val season_count: Int,
    val is_dubbed: Boolean,
    val is_subbed: Boolean = true,
    val content_descriptors: List<String> = emptyList(),
    val maturity_ratings: List<String> = emptyList()
)

@Serializable
data class RatingInfo(
    val average: String,
    val total: Int
)
