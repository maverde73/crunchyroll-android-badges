package com.maverde.crunchybadges.data.local.entities

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Data class that aggregates a Series with all its related data
 * Uses Room's @Relation annotation to automatically fetch related entities
 */
data class SeriesWithAllData(
    @Embedded
    val series: Series,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val metadata: SeriesMetadata?,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val rating: Rating?,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val images: List<Image>,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val awards: List<Award>,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val audioLocales: List<AudioLocale>,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val subtitleLocales: List<SubtitleLocale>,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val contentDescriptors: List<ContentDescriptor>,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val maturityRatings: List<MaturityRatingEntity>,

    @Relation(
        parentColumn = "id",
        entityColumn = "series_id"
    )
    val platforms: List<SeriesPlatform> = emptyList()
) {
    /**
     * Helper to get the best poster image (480x720 tall poster)
     */
    fun getPosterTallUrl(): String {
        return images
            .filter { it.type == "poster_tall" }
            .find { it.width == 480 && it.height == 720 }
            ?.sourceUrl ?: images.firstOrNull()?.sourceUrl ?: ""
    }

    /**
     * Helper to get the wide poster (1920x1080)
     */
    fun getPosterWideUrl(): String {
        return images
            .filter { it.type == "poster_wide" }
            .find { it.width == 1920 && it.height == 1080 }
            ?.sourceUrl ?: ""
    }

    /**
     * Helper to check if series has a specific audio locale
     */
    fun hasAudioLocale(locale: String): Boolean {
        return audioLocales.any { it.localeCode == locale }
    }

    /**
     * Helper to get maturity rating (prefer extended, fallback to maturity_ratings)
     */
    fun getMaturityRating(): String {
        return metadata?.extMaturityRating?.takeIf { it.isNotEmpty() }
            ?: maturityRatings.firstOrNull()?.ratingCode
            ?: ""
    }

    fun isOnCrunchyroll(): Boolean =
        platforms.any { it.platform == SeriesPlatform.PLATFORM_CRUNCHYROLL }

    fun isOnAnimeGeneration(): Boolean =
        platforms.any { it.platform == SeriesPlatform.PLATFORM_ANIME_GENERATION }

    fun animeGenerationDeepLink(): String? =
        platforms.firstOrNull { it.platform == SeriesPlatform.PLATFORM_ANIME_GENERATION }?.deepLinkUrl
}
