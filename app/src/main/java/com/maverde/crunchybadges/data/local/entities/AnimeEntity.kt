package com.maverde.crunchybadges.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing an anime series in the local database
 */
@Entity(tableName = "anime")
data class AnimeEntity(
    @PrimaryKey
    val id: String,  // Crunchyroll series ID (e.g., "GQWH0M990")

    // Basic info
    val title: String,
    val description: String,

    // Images
    val posterTallUrl: String,      // 480x720 poster
    val posterWideUrl: String,      // 1920x1080 wide poster

    // Metadata
    val rating: Double,             // Average rating (0.0-5.0)
    val episodeCount: Int,
    val seasonCount: Int,

    // Localization
    val audioLocalesJson: String,   // JSON array ["it-IT", "ja-JP", ...]
    val hasItalianAudio: Boolean,    // Quick filter flag

    // Content ratings
    val contentDescriptors: String,  // JSON array ["Violenza", ...]
    val maturityRating: String,      // e.g., "TV-14"

    // Timestamps
    val addedTimestamp: Long,        // When added to DB
    val updatedTimestamp: Long       // Last update
)
