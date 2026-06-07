package com.maverde.crunchybadges.data.models

import kotlinx.serialization.Serializable

/**
 * JSON contract published by the offline Anime Generation pipeline.
 * Schema mirrors docs/superpowers/specs/2026-06-07-anime-generation-multiplatform-design.md §5.
 */
@Serializable
data class AnimeGenerationCatalog(
    val version: Int,
    val generated_at: String,
    val titles: List<AnimeGenerationTitle> = emptyList()
)

@Serializable
data class AnimeGenerationTitle(
    val ag_id: String,
    val title: String,
    val year: Int? = null,
    val matched_crunchyroll_id: String? = null,
    val external_ids: AnimeGenerationExternalIds = AnimeGenerationExternalIds(),
    val description_it: String = "",
    val poster_tall: String = "",
    val poster_wide: String = "",
    val genres: List<String> = emptyList(),
    val rating: Double? = null,
    val audio_locales: List<String> = emptyList(),
    val subtitle_locales: List<String> = emptyList(),
    val languages_assumed: Boolean = false,
    val deep_link_url: String = "",
    val maturity_rating: String = ""
)

@Serializable
data class AnimeGenerationExternalIds(
    val mal_id: Int? = null,
    val anilist_id: Int? = null,
    val tmdb_id: Int? = null
)
