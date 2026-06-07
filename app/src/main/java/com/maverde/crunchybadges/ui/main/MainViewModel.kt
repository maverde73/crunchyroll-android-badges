package com.maverde.crunchybadges.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.local.entities.SeriesWithAllData
import com.maverde.crunchybadges.data.models.FilterState
import com.maverde.crunchybadges.data.preferences.FilterPreferences
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity
 * Manages series list from database (V2: updated for normalized schema + filters)
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AnimeDatabase.getDatabase(application)
    private val repository = AnimeRepository(database.animeDao(), application.applicationContext)
    private val filterPrefs = FilterPreferences(application.applicationContext)

    private val _seriesList = MutableStateFlow<List<SeriesWithAllData>>(emptyList())
    val seriesList: StateFlow<List<SeriesWithAllData>> = _seriesList

    // Platform is fixed by the active tab; it overrides any persisted platform.
    private var forcedPlatform: com.maverde.crunchybadges.data.models.PlatformFilter =
        com.maverde.crunchybadges.data.models.PlatformFilter.ALL

    private val _currentFilter = MutableStateFlow(
        filterPrefs.loadFilter().copy(platform = forcedPlatform)
    )
    val currentFilter: StateFlow<FilterState> = _currentFilter

    init {
        loadSeries()
    }

    /**
     * Set the platform shown by this list (called when a tab is selected).
     * Keeps the other persisted filters (audio, rating, sort) intact.
     */
    fun setPlatform(platform: com.maverde.crunchybadges.data.models.PlatformFilter) {
        forcedPlatform = platform
        _currentFilter.value = _currentFilter.value.copy(platform = platform)
        loadSeries()
    }

    /**
     * Load series from database with current filter
     */
    private fun loadSeries() {
        viewModelScope.launch {
            val filter = _currentFilter.value

            if (filter.hasActiveFilters() || filter.sortBy != com.maverde.crunchybadges.data.models.SortOption.TITLE_ASC) {
                // Use filtered query
                repository.getSeriesFiltered(filter).collect { series ->
                    _seriesList.value = series
                }
            } else {
                // No filters - use default query
                repository.getAllSeries().collect { series ->
                    _seriesList.value = series
                }
            }
        }
    }

    /**
     * Update filter and reload series
     */
    fun updateFilter(newFilter: FilterState) {
        // The tab owns the platform — keep it regardless of the filter sheet.
        val f = newFilter.copy(platform = forcedPlatform)
        _currentFilter.value = f
        filterPrefs.saveFilter(f)
        loadSeries()
    }

    /**
     * Refresh series list (for future pull-to-refresh)
     */
    fun refresh() {
        loadSeries()
    }
}
