package com.maverde.crunchybadges.data.models

/**
 * Filter and sort state for series list
 * All filters are applied with AND logic
 */
data class FilterState(
    // Audio locale filter (single selection)
    val audioLocale: String? = null,  // null = no filter, e.g., "it-IT", "en-US", "ja-JP"

    // Minimum rating filter (0.0 = no filter)
    val minRating: Double = 0.0,

    // Maturity rating filter (null = no filter)
    val maturityRating: String? = null,  // e.g., "TV-14", "16", "18"

    // Year range filter (null = no filter)
    val yearFrom: Int? = null,
    val yearTo: Int? = null,

    // Type filter (null = no filter)
    val type: String? = null,  // "series" or "movie"

    // Audio type filters (null = no filter)
    val isDubbed: Boolean? = null,
    val isSubbed: Boolean? = null,

    // Platform filter (ALL = no filter)
    val platform: PlatformFilter = PlatformFilter.ALL,

    // Sorting
    val sortBy: SortOption = SortOption.TITLE_ASC,
) {
    /**
     * Check if any filter is active (for UI indication)
     */
    fun hasActiveFilters(): Boolean {
        return audioLocale != null ||
                minRating > 0.0 ||
                maturityRating != null ||
                yearFrom != null ||
                yearTo != null ||
                type != null ||
                isDubbed != null ||
                isSubbed != null ||
                platform != PlatformFilter.ALL
    }

    /**
     * Reset all filters to default
     */
    fun reset(): FilterState {
        return FilterState(sortBy = sortBy)  // Keep only sort option
    }
}

/**
 * Platform filter options for series list
 */
enum class PlatformFilter(val displayName: String) {
    ALL("Tutte le piattaforme"),
    CRUNCHYROLL("Crunchyroll"),
    ANIME_GENERATION("Anime Generation")
}

/**
 * Sort options for series list
 */
enum class SortOption(
    val displayName: String,
    val sqlColumn: String,
    val sqlDirection: String
) {
    TITLE_ASC("Titolo (A-Z)", "title", "ASC"),
    TITLE_DESC("Titolo (Z-A)", "title", "DESC"),
    RATING_DESC("Rating (più alto)", "average", "DESC"),
    RATING_ASC("Rating (più basso)", "average", "ASC"),
    MATURITY_ASC("Età (dal più basso)", "ext_maturity_rating", "ASC"),
    MATURITY_DESC("Età (dal più alto)", "ext_maturity_rating", "DESC"),
    ADDED_DESC("Aggiunti di recente", "added_timestamp", "DESC"),
    ADDED_ASC("Aggiunti prima", "added_timestamp", "ASC"),
    EPISODES_DESC("Più episodi", "episode_count", "DESC"),
    EPISODES_ASC("Meno episodi", "episode_count", "ASC"),
    YEAR_DESC("Anno (più recente)", "series_launch_year", "DESC"),
    YEAR_ASC("Anno (più vecchio)", "series_launch_year", "ASC")
}
