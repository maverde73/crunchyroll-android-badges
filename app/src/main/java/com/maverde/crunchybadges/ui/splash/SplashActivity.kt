package com.maverde.crunchybadges.ui.splash

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.maverde.crunchybadges.MainActivity
import com.maverde.crunchybadges.R
import com.maverde.crunchybadges.scraping.ScrapingProgress
import kotlinx.coroutines.launch

/**
 * Splash screen that handles initial scraping or navigates to main activity
 */
class SplashActivity : AppCompatActivity() {

    private lateinit var viewModel: SplashViewModel
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var italianCountText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var errorText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize views
        progressBar = findViewById(R.id.progressBar)
        progressText = findViewById(R.id.progressText)
        italianCountText = findViewById(R.id.italianCountText)
        subtitleText = findViewById(R.id.subtitleText)
        errorText = findViewById(R.id.errorText)

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[SplashViewModel::class.java]

        // Observe scraping state
        lifecycleScope.launch {
            viewModel.scrapingState.collect { state ->
                handleScrapingState(state)
            }
        }

        // Check database and start scraping if needed
        lifecycleScope.launch {
            if (viewModel.isDatabaseEmpty()) {
                subtitleText.text = "Caricamento catalogo Crunchyroll..."
                viewModel.startScraping()
            } else {
                // Database already populated, go to main
                navigateToMain()
            }
        }
    }

    /**
     * Handle scraping state updates
     */
    private fun handleScrapingState(state: ScrapingProgress) {
        when (state) {
            is ScrapingProgress.Idle -> {
                // Do nothing
            }

            is ScrapingProgress.Initializing -> {
                subtitleText.text = "Inizializzazione..."
                progressBar.isIndeterminate = true
            }

            is ScrapingProgress.Scraping -> {
                progressBar.isIndeterminate = false
                progressBar.max = state.total
                progressBar.progress = state.current

                progressText.text = "${state.current} / ${state.total}"
                italianCountText.text = "${state.italianCount} anime doppiati in italiano"
                subtitleText.text = "Caricamento catalogo..."
            }

            is ScrapingProgress.Complete -> {
                subtitleText.text = "Completato!"
                progressBar.progress = progressBar.max
                italianCountText.text = "${state.italianCount} anime doppiati trovati!"

                // Navigate to main after short delay
                progressBar.postDelayed({
                    navigateToMain()
                }, 1000)
            }

            is ScrapingProgress.Error -> {
                subtitleText.text = "Errore"
                errorText.text = "Errore: ${state.message}"
                errorText.visibility = View.VISIBLE
                progressBar.isIndeterminate = false
            }
        }
    }

    /**
     * Navigate to main activity
     */
    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
