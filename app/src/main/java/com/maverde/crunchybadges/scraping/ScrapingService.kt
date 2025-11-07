package com.maverde.crunchybadges.scraping

import android.content.Context
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Service that orchestrates the scraping process
 * Coordinates WebViewScraper and Repository to fetch and store anime data
 */
class ScrapingService(
    private val context: Context,
    private val repository: AnimeRepository
) {

    private val scraper = WebViewScraper(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _progressFlow = MutableStateFlow<ScrapingProgress>(ScrapingProgress.Idle)
    val progressFlow: StateFlow<ScrapingProgress> = _progressFlow

    private var totalExpected = 0
    private var totalProcessed = 0
    private var italianCount = 0

    /**
     * Start the scraping process
     */
    fun startScraping() {
        _progressFlow.value = ScrapingProgress.Initializing

        scraper.initialize()

        // Listen to scraper results
        scope.launch {
            scraper.getResultsFlow().collect { result ->
                when (result) {
                    is ScrapingResult.Success -> {
                        handleSuccessResponse(result.response.data, result.response.total)
                    }
                    is ScrapingResult.Error -> {
                        _progressFlow.value = ScrapingProgress.Error(result.message)
                    }
                    is ScrapingResult.Complete -> {
                        _progressFlow.value = ScrapingProgress.Complete(italianCount)
                    }
                }
            }
        }

        // Start scraping
        scraper.startScraping()
        _progressFlow.value = ScrapingProgress.Scraping(0, 0, 0)
    }

    /**
     * Handle successful API response
     */
    private fun handleSuccessResponse(animeList: List<com.maverde.crunchybadges.data.models.AnimeData>, total: Int) {
        scope.launch(Dispatchers.IO) {
            try {
                // Update total if this is the first response
                if (totalExpected == 0) {
                    totalExpected = total
                }

                // Save to database
                repository.insertAnime(animeList)

                // Update counters
                totalProcessed += animeList.size
                italianCount = repository.getItalianAnimeCount()

                // Emit progress
                _progressFlow.value = ScrapingProgress.Scraping(
                    current = totalProcessed,
                    total = totalExpected,
                    italianCount = italianCount
                )

                android.util.Log.d(
                    "ScrapingService",
                    "Progress: $totalProcessed/$totalExpected, Italian: $italianCount"
                )

                // Check if complete
                if (totalProcessed >= totalExpected) {
                    _progressFlow.value = ScrapingProgress.Complete(italianCount)
                    destroy()
                }
            } catch (e: Exception) {
                android.util.Log.e("ScrapingService", "Error saving anime", e)
                _progressFlow.value = ScrapingProgress.Error(e.message ?: "Database error")
            }
        }
    }

    /**
     * Clean up resources
     */
    fun destroy() {
        scraper.destroy()
    }
}

/**
 * Sealed class for scraping progress states
 */
sealed class ScrapingProgress {
    object Idle : ScrapingProgress()
    object Initializing : ScrapingProgress()
    data class Scraping(
        val current: Int,
        val total: Int,
        val italianCount: Int
    ) : ScrapingProgress()
    data class Complete(val italianCount: Int) : ScrapingProgress()
    data class Error(val message: String) : ScrapingProgress()
}
