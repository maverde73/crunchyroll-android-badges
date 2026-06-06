package com.maverde.crunchybadges.ui.filters

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.maverde.crunchybadges.R
import com.maverde.crunchybadges.data.models.FilterState
import com.maverde.crunchybadges.data.models.PlatformFilter
import com.maverde.crunchybadges.data.models.SortOption

/**
 * Bottom sheet for selecting filters and sort options
 */
class FilterBottomSheet(
    private val currentFilter: FilterState,
    private val onApplyFilter: (FilterState) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var audioLocaleSpinner: Spinner
    private lateinit var minRatingSeekBar: SeekBar
    private lateinit var ratingValueText: TextView
    private lateinit var sortBySpinner: Spinner
    private lateinit var platformSpinner: Spinner

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_filters, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        audioLocaleSpinner = view.findViewById(R.id.audioLocaleSpinner)
        minRatingSeekBar = view.findViewById(R.id.minRatingSeekBar)
        ratingValueText = view.findViewById(R.id.ratingValueText)
        sortBySpinner = view.findViewById(R.id.sortBySpinner)
        platformSpinner = view.findViewById(R.id.platformSpinner)

        setupAudioLocaleSpinner()
        setupRatingSeekBar()
        setupSortSpinner()
        setupPlatformSpinner()
        setupButtons(view)

        // Load current filter values
        loadCurrentFilter()

        // Force expand bottom sheet for Fire TV
        dialog?.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as? com.google.android.material.bottomsheet.BottomSheetDialog)
                ?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = com.google.android.material.bottomsheet.BottomSheetBehavior.from(it)
                behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    private fun setupAudioLocaleSpinner() {
        val locales = listOf(
            "Tutte" to null,
            "🇮🇹 Italiano" to "it-IT",
            "🇺🇸 English" to "en-US",
            "🇯🇵 日本語" to "ja-JP",
            "🇪🇸 Español (ES)" to "es-ES",
            "🌎 Español (LAT)" to "es-419",
            "🇩🇪 Deutsch" to "de-DE",
            "🇫🇷 Français" to "fr-FR",
            "🇧🇷 Português" to "pt-BR",
            "🇷🇺 Русский" to "ru-RU"
        )

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            locales.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        audioLocaleSpinner.adapter = adapter
    }

    private fun setupRatingSeekBar() {
        minRatingSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val rating = progress / 10.0
                ratingValueText.text = if (rating > 0) String.format("%.1f stelle", rating) else "Tutti"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSortSpinner() {
        val sortOptions = SortOption.values()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            sortOptions.map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sortBySpinner.adapter = adapter
    }

    private fun setupPlatformSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            PlatformFilter.values().map { it.displayName }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        platformSpinner.adapter = adapter
    }

    private fun setupButtons(view: View) {
        view.findViewById<Button>(R.id.clearFiltersButton).setOnClickListener {
            loadCurrentFilter()  // Reset to current
            onApplyFilter(currentFilter.reset())
            dismiss()
        }

        view.findViewById<Button>(R.id.applyFiltersButton).setOnClickListener {
            applyFilter()
        }
    }

    private fun loadCurrentFilter() {
        // Audio locale
        val locales = listOf(null, "it-IT", "en-US", "ja-JP", "es-ES", "es-419", "de-DE", "fr-FR", "pt-BR", "ru-RU")
        audioLocaleSpinner.setSelection(locales.indexOf(currentFilter.audioLocale))

        // Rating
        minRatingSeekBar.progress = (currentFilter.minRating * 10).toInt()

        // Sort
        sortBySpinner.setSelection(currentFilter.sortBy.ordinal)

        // Platform
        platformSpinner.setSelection(currentFilter.platform.ordinal)
    }

    private fun applyFilter() {
        val locales = listOf(null, "it-IT", "en-US", "ja-JP", "es-ES", "es-419", "de-DE", "fr-FR", "pt-BR", "ru-RU")

        val newFilter = FilterState(
            audioLocale = locales[audioLocaleSpinner.selectedItemPosition],
            minRating = minRatingSeekBar.progress / 10.0,
            platform = PlatformFilter.values()[platformSpinner.selectedItemPosition],
            sortBy = SortOption.values()[sortBySpinner.selectedItemPosition]
        )

        onApplyFilter(newFilter)
        dismiss()
    }
}
