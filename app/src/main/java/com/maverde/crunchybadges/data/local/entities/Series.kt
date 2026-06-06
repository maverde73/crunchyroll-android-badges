package com.maverde.crunchybadges.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Main series entity
 * Maps to the "series" table
 */
@Entity(tableName = "series")
data class Series(
    @PrimaryKey
    val id: String,

    val title: String,

    @ColumnInfo(name = "slug_title")
    val slugTitle: String? = null,

    val description: String,

    val type: String,  // "series", "movie", etc.

    @ColumnInfo(name = "is_new")
    val isNew: Boolean = false,

    @ColumnInfo(name = "last_public")
    val lastPublic: String? = null,  // ISO 8601 timestamp

    @ColumnInfo(name = "channel_id")
    val channelId: String? = null,

    @ColumnInfo(name = "external_id")
    val externalId: String? = null,

    @ColumnInfo(name = "linked_resource_key")
    val linkedResourceKey: String? = null,  // e.g., "cms:/series/GXJHM3P5W"

    @ColumnInfo(name = "promo_description")
    val promoDescription: String? = null,

    @ColumnInfo(name = "promo_title")
    val promoTitle: String? = null,

    val slug: String? = null,

    @ColumnInfo(name = "mal_id")
    val malId: Int? = null,

    @ColumnInfo(name = "anilist_id")
    val anilistId: Int? = null,

    @ColumnInfo(name = "tmdb_id")
    val tmdbId: Int? = null,

    @ColumnInfo(name = "added_timestamp")
    val addedTimestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_timestamp")
    val updatedTimestamp: Long = System.currentTimeMillis()
)
