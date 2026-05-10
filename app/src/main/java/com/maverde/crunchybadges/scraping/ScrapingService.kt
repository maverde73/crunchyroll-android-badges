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
    private val repository: AnimeRepository,
    private val syncMode: SyncMode = SyncMode.FULL_SYNC
) {

    private val sortBy = when (syncMode) {
        SyncMode.FULL_SYNC -> "alphabetical"
        SyncMode.INCREMENTAL_SYNC -> "newly_added"
    }

    private val scraper = WebViewScraper(context, sortBy)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _progressFlow = MutableStateFlow<ScrapingProgress>(ScrapingProgress.Idle)
    val progressFlow: StateFlow<ScrapingProgress> = _progressFlow

    private var totalExpected = 0
    private var totalProcessed = 0
    private var italianCount = 0
    private var foundExistingSeries = false  // For incremental sync

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

                // INCREMENTAL SYNC: stop when we hit a series we already have, but
                // first persist any NEW items present in this final batch — save every
                // new item, not just those before the first existing, for robustness
                // against imperfect API ordering.
                if (syncMode == SyncMode.INCREMENTAL_SYNC && !foundExistingSeries) {
                    val classified = animeList.map { it to repository.seriesExists(it.id) }
                    val firstExistingIndex = classified.indexOfFirst { it.second }

                    if (firstExistingIndex >= 0) {
                        val newInBatch = classified.filter { !it.second }.map { it.first }
                        if (newInBatch.isNotEmpty()) {
                            repository.insertAnime(newInBatch)
                            totalProcessed += newInBatch.size
                        }

                        val existing = classified[firstExistingIndex].first
                        android.util.Log.d(
                            "ScrapingService",
                            "✅ Found existing series: ${existing.title} (${existing.id}). Stopping incremental sync."
                        )
                        foundExistingSeries = true

                        italianCount = repository.getItalianSeriesCount()
                        _progressFlow.value = ScrapingProgress.Complete(italianCount)
                        destroy()
                        return@launch
                    }
                }

                // Save to database (only new series in INCREMENTAL mode)
                repository.insertAnime(animeList)

                // Update counters
                totalProcessed += animeList.size
                italianCount = repository.getItalianSeriesCount()

                // Emit progress
                val displayTotal = if (syncMode == SyncMode.INCREMENTAL_SYNC) {
                    totalProcessed  // Unknown total for incremental
                } else {
                    totalExpected
                }

                _progressFlow.value = ScrapingProgress.Scraping(
                    current = totalProcessed,
                    total = displayTotal,
                    italianCount = italianCount
                )

                android.util.Log.d(
                    "ScrapingService",
                    "Progress: $totalProcessed/${if (syncMode == SyncMode.INCREMENTAL_SYNC) "?" else totalExpected}, Italian: $italianCount"
                )

                // Check if complete (FULL SYNC only - INCREMENTAL stops when finding existing series)
                if (syncMode == SyncMode.FULL_SYNC && totalProcessed >= totalExpected) {
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
