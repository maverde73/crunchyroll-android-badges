package com.maverde.crunchybadges.ui.detail

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.maverde.crunchybadges.R
import com.maverde.crunchybadges.SettingsActivity
import com.maverde.crunchybadges.data.local.entities.SeriesWithAllData
import com.maverde.crunchybadges.data.local.entities.TranslatedDescription
import com.maverde.crunchybadges.data.preferences.TranslationPreferences
import com.maverde.crunchybadges.data.repository.AnimeRepository
import com.maverde.crunchybadges.data.translation.TranslationError
import com.maverde.crunchybadges.data.translation.TranslationResult
import com.maverde.crunchybadges.data.translation.TranslationService
import com.maverde.crunchybadges.utils.LocaleHelper
import kotlinx.coroutines.launch

/**
 * Detail screen showing complete information about a series
 */
class DetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERIES_ID = "series_id"
    }

    private lateinit var repository: AnimeRepository

    private lateinit var posterWideImage: ImageView
    private lateinit var titleText: TextView
    private lateinit var infoText: TextView
    private lateinit var ratingContainer: View
    private lateinit var ratingText: TextView
    private lateinit var ratingVotesText: TextView
    private lateinit var maturityRatingText: TextView
    private lateinit var descriptionText: TextView
    private lateinit var audioLocalesText: TextView
    private lateinit var subtitleLocalesText: TextView
    private lateinit var contentDescriptorsLabel: TextView
    private lateinit var contentDescriptorsText: TextView
    private lateinit var awardsLabel: TextView
    private lateinit var awardsText: TextView
    private lateinit var openCrunchyrollButton: Button
    private lateinit var openPrimeVideoButton: Button

    // Translation views
    private lateinit var translateButton: Button
    private lateinit var translationProgress: ProgressBar
    private lateinit var languageToggle: RadioGroup
    private lateinit var radioOriginal: RadioButton
    private lateinit var radioItaliano: RadioButton
    private lateinit var culturalNotesText: TextView

    // Translation state
    private var originalDescription: String = ""
    private var translatedDescription: String? = null
    private var culturalNotes: String? = null
    private lateinit var translationPrefs: TranslationPreferences

    private var currentSeriesData: SeriesWithAllData? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        val database = com.maverde.crunchybadges.data.local.database.AnimeDatabase.getDatabase(application)
        repository = AnimeRepository(database.animeDao(), application)

        // Initialize views
        posterWideImage = findViewById(R.id.posterWideImage)
        titleText = findViewById(R.id.titleText)
        infoText = findViewById(R.id.infoText)
        ratingContainer = findViewById(R.id.ratingContainer)
        ratingText = findViewById(R.id.ratingText)
        ratingVotesText = findViewById(R.id.ratingVotesText)
        maturityRatingText = findViewById(R.id.maturityRatingText)
        descriptionText = findViewById(R.id.descriptionText)
        audioLocalesText = findViewById(R.id.audioLocalesText)
        subtitleLocalesText = findViewById(R.id.subtitleLocalesText)
        contentDescriptorsLabel = findViewById(R.id.contentDescriptorsLabel)
        contentDescriptorsText = findViewById(R.id.contentDescriptorsText)
        awardsLabel = findViewById(R.id.awardsLabel)
        awardsText = findViewById(R.id.awardsText)
        openCrunchyrollButton = findViewById(R.id.openCrunchyrollButton)
        openPrimeVideoButton = findViewById(R.id.openPrimeVideoButton)

        // Translation views
        translateButton = findViewById(R.id.translateButton)
        translationProgress = findViewById(R.id.translationProgress)
        languageToggle = findViewById(R.id.languageToggle)
        radioOriginal = findViewById(R.id.radioOriginal)
        radioItaliano = findViewById(R.id.radioItaliano)
        culturalNotesText = findViewById(R.id.culturalNotesText)

        // Initialize translation preferences
        translationPrefs = TranslationPreferences(this)

        // Setup translation controls
        setupTranslationControls()

        // Get series ID from intent
        val seriesId = intent.getStringExtra(EXTRA_SERIES_ID)
        if (seriesId != null) {
            loadSeriesData(seriesId)
        } else {
            android.util.Log.e("DetailActivity", "No series ID provided")
            finish()
        }

        // Setup Crunchyroll button
        openCrunchyrollButton.setOnClickListener {
            openCrunchyrollPage()
        }

        // Setup Prime Video button (Anime Generation)
        openPrimeVideoButton.setOnClickListener {
            val link = currentSeriesData?.animeGenerationDeepLink()
            if (link != null) {
                com.maverde.crunchybadges.IntentLauncher(this).launchPrimeVideo(link)
            }
        }
    }

    private fun loadSeriesData(seriesId: String) {
        lifecycleScope.launch {
            try {
                val seriesData = repository.getSeriesById(seriesId)
                if (seriesData != null) {
                    currentSeriesData = seriesData
                    displaySeriesData(seriesData)
                } else {
                    android.util.Log.e("DetailActivity", "Series not found: $seriesId")
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("DetailActivity", "Error loading series", e)
                finish()
            }
        }
    }

    private fun displaySeriesData(seriesData: SeriesWithAllData) {
        val series = seriesData.series
        val metadata = seriesData.metadata
        val rating = seriesData.rating

        // Title
        titleText.text = series.title

        // Platform-aware action buttons
        openCrunchyrollButton.visibility = if (seriesData.isOnCrunchyroll()) View.VISIBLE else View.GONE
        openPrimeVideoButton.visibility = if (seriesData.animeGenerationDeepLink() != null) View.VISIBLE else View.GONE

        // Wide poster
        val posterUrl = seriesData.getPosterWideUrl()
        if (posterUrl.isNotEmpty()) {
            posterWideImage.load(posterUrl) {
                crossfade(true)
                placeholder(R.drawable.icon48)
                error(R.drawable.icon48)
            }
        }

        // Info row: Year • Seasons • Episodes • Type
        val infoItems = mutableListOf<String>()
        metadata?.seriesLaunchYear?.let { infoItems.add(it.toString()) }
        metadata?.seasonCount?.let { if (it > 0) infoItems.add("$it stagion${if (it > 1) "i" else "e"}") }
        metadata?.episodeCount?.let { if (it > 0) infoItems.add("$it episod${if (it > 1) "i" else "io"}") }
        series.type?.let { infoItems.add(if (it == "series") "Serie" else it.replaceFirstChar { c -> c.uppercase() }) }
        infoText.text = infoItems.joinToString(" • ")

        // Rating
        val ratingAverage = rating?.average ?: 0.0
        if (ratingAverage > 0) {
            ratingContainer.visibility = View.VISIBLE
            ratingText.text = String.format("%.1f", ratingAverage)
            val totalVotes = rating?.total ?: 0
            ratingVotesText.text = "($totalVotes vot${if (totalVotes > 1) "i" else "o"})"
        } else {
            ratingContainer.visibility = View.GONE
        }

        // Maturity rating
        val maturityRating = seriesData.getMaturityRating()
        if (maturityRating.isNotEmpty()) {
            maturityRatingText.visibility = View.VISIBLE
            maturityRatingText.text = maturityRating
        } else {
            maturityRatingText.visibility = View.GONE
        }

        // Description
        originalDescription = series.description
        descriptionText.text = originalDescription.takeIf { it.isNotEmpty() } ?: "Nessuna descrizione disponibile."

        // Check for cached translation
        if (originalDescription.isNotEmpty()) {
            checkCachedTranslation(series.id)
        } else {
            // Hide translate button if no description
            translateButton.visibility = View.GONE
        }

        // Audio locales with flags
        if (seriesData.audioLocales.isNotEmpty()) {
            val audioText = seriesData.audioLocales.mapNotNull { audioLocale ->
                val flag = LocaleHelper.getFlagEmoji(audioLocale.localeCode)
                if (flag != null) "$flag ${getLocaleName(audioLocale.localeCode)}" else null
            }.joinToString("  ")
            audioLocalesText.text = audioText.ifEmpty { "Nessuna lingua audio disponibile" }
        } else {
            audioLocalesText.text = "Nessuna lingua audio disponibile"
        }

        // Subtitle locales
        if (seriesData.subtitleLocales.isNotEmpty()) {
            val subtitleText = seriesData.subtitleLocales
                .map { getLocaleName(it.localeCode) }
                .joinToString(", ")
            subtitleLocalesText.text = subtitleText
        } else {
            subtitleLocalesText.text = "Nessun sottotitolo disponibile"
        }

        // Content descriptors
        if (seriesData.contentDescriptors.isNotEmpty()) {
            contentDescriptorsLabel.visibility = View.VISIBLE
            contentDescriptorsText.visibility = View.VISIBLE
            contentDescriptorsText.text = seriesData.contentDescriptors
                .map { it.descriptor }
                .joinToString(", ")
        } else {
            contentDescriptorsLabel.visibility = View.GONE
            contentDescriptorsText.visibility = View.GONE
        }

        // Awards
        if (seriesData.awards.isNotEmpty()) {
            awardsLabel.visibility = View.VISIBLE
            awardsText.visibility = View.VISIBLE
            awardsText.text = seriesData.awards
                .mapNotNull { it.text }
                .joinToString(", ")
        } else {
            awardsLabel.visibility = View.GONE
            awardsText.visibility = View.GONE
        }
    }

    private fun getLocaleName(localeCode: String): String {
        return when (localeCode) {
            "it-IT" -> "Italiano"
            "en-US" -> "English"
            "ja-JP" -> "日本語"
            "es-ES" -> "Español (ES)"
            "es-419" -> "Español (LAT)"
            "de-DE" -> "Deutsch"
            "fr-FR" -> "Français"
            "pt-BR" -> "Português"
            "ru-RU" -> "Русский"
            "ar-SA" -> "العربية"
            "hi-IN" -> "हिन्दी"
            "zh-CN" -> "中文 (简体)"
            "zh-TW" -> "中文 (繁體)"
            "ko-KR" -> "한국어"
            "tr-TR" -> "Türkçe"
            "pl-PL" -> "Polski"
            "th-TH" -> "ไทย"
            "vi-VN" -> "Tiếng Việt"
            "id-ID" -> "Bahasa Indonesia"
            else -> localeCode
        }
    }

    private fun openCrunchyrollPage() {
        val seriesData = currentSeriesData ?: return
        val series = seriesData.series
        val slug = series.slugTitle?.takeIf { it.isNotEmpty() } ?: series.slug ?: ""

        // Build Crunchyroll deep link: crunchyroll://series/{id}/{slug}
        val deepLink = "crunchyroll://series/${series.id}/${slug}"
        android.util.Log.d("DetailActivity", "Opening Crunchyroll with deep link: $deepLink")

        try {
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(deepLink)
            intent.setPackage("com.crunchyroll.crunchyroid")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            startActivity(intent)
            android.util.Log.d("DetailActivity", "Successfully opened Crunchyroll app with deep link")
        } catch (e: Exception) {
            android.util.Log.e("DetailActivity", "Failed to open Crunchyroll with deep link", e)
            showCrunchyrollError()
        }
    }

    private fun showCrunchyrollError() {
        Toast.makeText(
            this,
            "App Crunchyroll non installata. Installala da Amazon Appstore.",
            Toast.LENGTH_LONG
        ).show()
    }

    // ==================== TRANSLATION METHODS ====================

    /**
     * Setup translation button and toggle listeners
     */
    private fun setupTranslationControls() {
        translateButton.setOnClickListener {
            if (!translationPrefs.hasApiKey()) {
                showApiKeyPrompt()
                return@setOnClickListener
            }
            translateDescription()
        }

        languageToggle.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioOriginal -> {
                    descriptionText.text = originalDescription
                    culturalNotesText.visibility = View.GONE
                }
                R.id.radioItaliano -> {
                    descriptionText.text = translatedDescription ?: originalDescription
                    if (!culturalNotes.isNullOrBlank()) {
                        culturalNotesText.text = "Note: $culturalNotes"
                        culturalNotesText.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    /**
     * Translate the description using Gemini API
     */
    private fun translateDescription() {
        val seriesId = currentSeriesData?.series?.id ?: return
        val textToTranslate = originalDescription

        if (textToTranslate.isBlank()) {
            Toast.makeText(this, "Nessuna descrizione da tradurre", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            // Check cache first
            val cached = repository.getTranslation(seriesId)
            if (cached != null) {
                android.util.Log.d("DetailActivity", "Using cached translation for $seriesId")
                showTranslation(cached.translatedDescription, cached.culturalNotes)
                return@launch
            }

            // Show loading state
            translateButton.visibility = View.GONE
            translationProgress.visibility = View.VISIBLE

            // Call Gemini API
            val service = TranslationService(translationPrefs.getApiKey())
            when (val result = service.translate(textToTranslate)) {
                is TranslationResult.Success -> {
                    android.util.Log.d("DetailActivity", "Translation successful")

                    // Cache result
                    repository.saveTranslation(
                        TranslatedDescription(
                            seriesId = seriesId,
                            translatedDescription = result.translation,
                            culturalNotes = result.culturalNotes,
                            modelUsed = "gemini-2.5-flash-lite"
                        )
                    )
                    showTranslation(result.translation, result.culturalNotes)
                }
                is TranslationResult.Error -> {
                    android.util.Log.e("DetailActivity", "Translation error: ${result.error}")
                    showTranslationError(result.error)
                }
            }

            translationProgress.visibility = View.GONE
        }
    }

    /**
     * Show the translated description in the UI
     */
    private fun showTranslation(translation: String?, notes: String?) {
        translatedDescription = translation
        culturalNotes = notes

        translateButton.visibility = View.GONE
        languageToggle.visibility = View.VISIBLE
        radioItaliano.isChecked = true

        descriptionText.text = translation ?: originalDescription

        if (!notes.isNullOrBlank()) {
            culturalNotesText.text = "Note: $notes"
            culturalNotesText.visibility = View.VISIBLE
        }
    }

    /**
     * Show translation error message
     */
    private fun showTranslationError(error: TranslationError) {
        translateButton.visibility = View.VISIBLE
        Toast.makeText(this, error.getDisplayMessage(), Toast.LENGTH_LONG).show()
    }

    /**
     * Show dialog prompting user to configure API key
     */
    private fun showApiKeyPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Chiave API richiesta")
            .setMessage("Per tradurre le descrizioni serve una chiave API Google Gemini.\n\nVuoi configurarla ora nelle impostazioni?")
            .setPositiveButton("Configura") { _, _ ->
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    /**
     * Check for cached translation when series is loaded
     */
    private fun checkCachedTranslation(seriesId: String) {
        lifecycleScope.launch {
            val cached = repository.getTranslation(seriesId)
            if (cached != null) {
                translatedDescription = cached.translatedDescription
                culturalNotes = cached.culturalNotes

                // Show toggle instead of translate button
                translateButton.visibility = View.GONE
                languageToggle.visibility = View.VISIBLE
                radioOriginal.isChecked = true  // Start with original
            }
        }
    }
}
