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
