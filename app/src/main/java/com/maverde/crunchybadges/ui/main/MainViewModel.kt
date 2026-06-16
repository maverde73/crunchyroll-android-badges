package com.maverde.crunchybadges.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.local.entities.SeriesWithAllData
import com.maverde.crunchybadges.data.models.FilterState
import com.maverde.crunchybadges.data.preferences.FilterPreferences
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for MainActivity
 * Manages series list from database (V2: updated for normalized schema + filters)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AnimeDatabase.getDatabase(application)
    private val repository = AnimeRepository(database.animeDao(), application.applicationContext)
    private val filterPrefs = FilterPreferences(application.applicationContext)

    // Platform is fixed by the active tab; it overrides any persisted platform.
    private var forcedPlatform: com.maverde.crunchybadges.data.models.PlatformFilter =
        com.maverde.crunchybadges.data.models.PlatformFilter.ALL

    private val _currentFilter = MutableStateFlow(
        filterPrefs.loadFilter().copy(platform = forcedPlatform)
    )
    val currentFilter: StateFlow<FilterState> = _currentFilter

    /**
     * Single reactive pipeline: the list is derived from [_currentFilter].
     *
     * `flatMapLatest` CANCELS the previous database query the instant the filter
     * changes, so exactly one query/ordering is ever active. Previously each tab
     * switch and filter change launched another collector that was never
     * cancelled; the leftover collectors kept pushing their own (stale) orderings
     * into the list, and the grid reshuffled endlessly. See SeriesListPipelineTest.
     */
    val seriesList: StateFlow<List<SeriesWithAllData>> =
        _currentFilter
            .flatMapLatest { filter ->
                if (filter.hasActiveFilters() ||
                    filter.sortBy != com.maverde.crunchybadges.data.models.SortOption.TITLE_ASC
                ) {
                    repository.getSeriesFiltered(filter)
                } else {
                    repository.getAllSeries()
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Set the platform shown by this list (called when a tab is selected).
     * Keeps the other persisted filters (audio, rating, sort) intact.
     */
    fun setPlatform(platform: com.maverde.crunchybadges.data.models.PlatformFilter) {
        forcedPlatform = platform
        _currentFilter.value = _currentFilter.value.copy(platform = platform)
    }

    /**
     * Update filter (the list re-queries automatically via the flatMapLatest pipeline).
     */
    fun updateFilter(newFilter: FilterState) {
        // The tab owns the platform — keep it regardless of the filter sheet.
        val f = newFilter.copy(platform = forcedPlatform)
        _currentFilter.value = f
        filterPrefs.saveFilter(f)
    }
}
