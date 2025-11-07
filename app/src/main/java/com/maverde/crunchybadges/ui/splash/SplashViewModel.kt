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
    private val repository = AnimeRepository(database.animeDao())
    private lateinit var scrapingService: ScrapingService

    private val _scrapingState = MutableStateFlow<ScrapingProgress>(ScrapingProgress.Idle)
    val scrapingState: StateFlow<ScrapingProgress> = _scrapingState

    /**
     * Check if database is empty
     */
    suspend fun isDatabaseEmpty(): Boolean {
        return repository.getItalianAnimeCount() == 0
    }

    /**
     * Get current Italian anime count
     */
    suspend fun getItalianCount(): Int {
        return repository.getItalianAnimeCount()
    }

    /**
     * Start scraping process
     */
    fun startScraping() {
        scrapingService = ScrapingService(getApplication(), repository)

        // Collect scraping progress
        viewModelScope.launch {
            scrapingService.progressFlow.collect { progress ->
                _scrapingState.value = progress
            }
        }

        // Start scraping
        scrapingService.startScraping()
    }

    override fun onCleared() {
        super.onCleared()
        if (::scrapingService.isInitialized) {
            scrapingService.destroy()
        }
    }
}
