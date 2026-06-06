package com.maverde.crunchybadges.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.maverde.crunchybadges.data.models.FilterState
import com.maverde.crunchybadges.data.models.PlatformFilter
import com.maverde.crunchybadges.data.models.SortOption

/**
 * Manages filter and sort preferences in SharedPreferences
 * Filters persist across app restarts
 */
class FilterPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "filter_preferences"

        private const val KEY_AUDIO_LOCALE = "audio_locale"
        private const val KEY_MIN_RATING = "min_rating"
        private const val KEY_MATURITY_RATING = "maturity_rating"
        private const val KEY_YEAR_FROM = "year_from"
        private const val KEY_YEAR_TO = "year_to"
        private const val KEY_TYPE = "type"
        private const val KEY_IS_DUBBED = "is_dubbed"
        private const val KEY_IS_SUBBED = "is_subbed"
        private const val KEY_PLATFORM = "platform"
        private const val KEY_SORT_BY = "sort_by"
    }

    /**
     * Save filter state to SharedPreferences
     */
    fun saveFilter(filter: FilterState) {
        prefs.edit().apply {
            // Nullable string filters
            putString(KEY_AUDIO_LOCALE, filter.audioLocale)
            putString(KEY_MATURITY_RATING, filter.maturityRating)
            putString(KEY_TYPE, filter.type)

            // Rating filter
            putFloat(KEY_MIN_RATING, filter.minRating.toFloat())

            // Year range (use -1 for null)
            putInt(KEY_YEAR_FROM, filter.yearFrom ?: -1)
            putInt(KEY_YEAR_TO, filter.yearTo ?: -1)

            // Boolean filters (use -1 for null, 0 for false, 1 for true)
            putInt(KEY_IS_DUBBED, filter.isDubbed?.let { if (it) 1 else 0 } ?: -1)
            putInt(KEY_IS_SUBBED, filter.isSubbed?.let { if (it) 1 else 0 } ?: -1)

            // Platform filter
            putString(KEY_PLATFORM, filter.platform.name)

            // Sort option
            putString(KEY_SORT_BY, filter.sortBy.name)

            apply()
        }
    }

    /**
     * Load filter state from SharedPreferences
     */
    fun loadFilter(): FilterState {
        return FilterState(
            audioLocale = prefs.getString(KEY_AUDIO_LOCALE, null),
            minRating = prefs.getFloat(KEY_MIN_RATING, 0.0f).toDouble(),
            maturityRating = prefs.getString(KEY_MATURITY_RATING, null),
            yearFrom = prefs.getInt(KEY_YEAR_FROM, -1).takeIf { it != -1 },
            yearTo = prefs.getInt(KEY_YEAR_TO, -1).takeIf { it != -1 },
            type = prefs.getString(KEY_TYPE, null),
            isDubbed = when (prefs.getInt(KEY_IS_DUBBED, -1)) {
                1 -> true
                0 -> false
                else -> null
            },
            isSubbed = when (prefs.getInt(KEY_IS_SUBBED, -1)) {
                1 -> true
                0 -> false
                else -> null
            },
            platform = try {
                PlatformFilter.valueOf(prefs.getString(KEY_PLATFORM, PlatformFilter.ALL.name) ?: PlatformFilter.ALL.name)
            } catch (e: IllegalArgumentException) {
                PlatformFilter.ALL  // Fallback to default
            },
            sortBy = try {
                SortOption.valueOf(prefs.getString(KEY_SORT_BY, SortOption.TITLE_ASC.name) ?: SortOption.TITLE_ASC.name)
            } catch (e: IllegalArgumentException) {
                SortOption.TITLE_ASC  // Fallback to default
            }
        )
    }

    /**
     * Clear all filters (reset to default)
     */
    fun clearFilters() {
        val currentSort = loadFilter().sortBy  // Preserve sort
        prefs.edit().clear().apply()
        saveFilter(FilterState(sortBy = currentSort))
    }
}
