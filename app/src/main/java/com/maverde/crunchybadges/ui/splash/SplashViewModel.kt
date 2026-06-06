package com.maverde.crunchybadges.ui.splash

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.repository.AnimeRepository
import com.maverde.crunchybadges.scraping.ScrapingProgress
import com.maverde.crunchybadges.scraping.ScrapingService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for SplashActivity
 * Manages scraping state and database checks
 */
class SplashViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AnimeDatabase.getDatabase(application)
    private val repository = AnimeRepository(database.animeDao(), application.applicationContext)
    private lateinit var scrapingService: ScrapingService

    private val _scrapingState = MutableStateFlow<ScrapingProgress>(ScrapingProgress.Idle)
    val scrapingState: StateFlow<ScrapingProgress> = _scrapingState

    /**
     * Check if database is empty
     * V2: Now checks total series count (we download ALL series, not just Italian)
     */
    suspend fun isDatabaseEmpty(): Boolean {
        return repository.getTotalSeriesCount() == 0
    }

    /**
     * Get current series count with Italian audio
     */
    suspend fun getItalianCount(): Int {
        return repository.getItalianSeriesCount()
    }

    /**
     * Start scraping process
     * Uses FULL_SYNC if database is empty, INCREMENTAL_SYNC otherwise
     */
    fun startScraping() {
        viewModelScope.launch {
            // Kick off Anime Generation catalog sync (independent of CR scrape).
            com.maverde.crunchybadges.sync.AnimeGenerationSyncScheduler.syncNow(getApplication())
            com.maverde.crunchybadges.sync.AnimeGenerationSyncScheduler.schedulePeriodic(getApplication())

            // Determine sync mode based on database state
            val syncMode = if (isDatabaseEmpty()) {
                android.util.Log.d("SplashViewModel", "Database empty → FULL_SYNC (alphabetical)")
                com.maverde.crunchybadges.scraping.SyncMode.FULL_SYNC
            } else {
                android.util.Log.d("SplashViewModel", "Database has data → INCREMENTAL_SYNC (newly_added)")
                com.maverde.crunchybadges.scraping.SyncMode.INCREMENTAL_SYNC
            }

            scrapingService = ScrapingService(getApplication(), repository, syncMode)

            // Collect scraping progress
            launch {
                scrapingService.progressFlow.collect { progress ->
                    _scrapingState.value = progress
                }
            }

            // Start scraping
            scrapingService.startScraping()
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (::scrapingService.isInitialized) {
            scrapingService.destroy()
        }
    }
}
