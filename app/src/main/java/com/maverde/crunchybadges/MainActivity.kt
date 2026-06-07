package com.maverde.crunchybadges

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.maverde.crunchybadges.ui.detail.DetailActivity
import com.maverde.crunchybadges.ui.filters.FilterBottomSheet
import com.maverde.crunchybadges.ui.main.AnimeListAdapter
import com.maverde.crunchybadges.ui.main.MainViewModel
import kotlinx.coroutines.launch

/**
 * Main Activity - v3.0 Native Catalog
 *
 * Displays Italian-dubbed anime from local database
 * Uses native RecyclerView instead of WebView
 */
class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: MainViewModel
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    private lateinit var adapter: AnimeListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView)
        emptyText = findViewById(R.id.emptyText)

        // Setup RecyclerView
        adapter = AnimeListAdapter { seriesData ->
            // Open DetailActivity
            val intent = Intent(this, DetailActivity::class.java)
            intent.putExtra(DetailActivity.EXTRA_SERIES_ID, seriesData.series.id)
            startActivity(intent)
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(this, getGridSpanCount())

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Setup platform tabs (Crunchyroll | Anime Generation)
        setupTabs()

        // Observe series list (V2: updated for normalized schema)
        lifecycleScope.launch {
            viewModel.seriesList.collect { seriesList ->
                if (seriesList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyText.visibility = View.GONE
                    adapter.submitList(seriesList)
                }
            }
        }
    }

    /**
     * Setup the two platform tabs. Selecting a tab swaps the grid to that
     * platform's catalog (the grid is shared; content changes).
     */
    private fun setupTabs() {
        val tabs = findViewById<com.google.android.material.tabs.TabLayout>(R.id.platformTabs)
        tabs.addTab(tabs.newTab().setText("Crunchyroll"))
        tabs.addTab(tabs.newTab().setText("Anime Generation"))
        tabs.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab) {
                val platform = if (tab.position == 0)
                    com.maverde.crunchybadges.data.models.PlatformFilter.CRUNCHYROLL
                else
                    com.maverde.crunchybadges.data.models.PlatformFilter.ANIME_GENERATION
                viewModel.setPlatform(platform)
                recyclerView.scrollToPosition(0)
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab) {}
        })
        // Default to the Crunchyroll tab.
        viewModel.setPlatform(com.maverde.crunchybadges.data.models.PlatformFilter.CRUNCHYROLL)
    }

    /**
     * Determine grid column count based on screen width
     * Fire TV typically has wider screens
     */
    private fun getGridSpanCount(): Int {
        val displayMetrics = resources.displayMetrics
        val screenWidthDp = displayMetrics.widthPixels / displayMetrics.density
        return when {
            screenWidthDp >= 1200 -> 4  // Very wide screens (Fire TV, tablets)
            screenWidthDp >= 800 -> 3   // Tablets
            screenWidthDp >= 600 -> 2   // Large phones
            else -> 1                    // Phones
        }
    }

    /**
     * Handle Fire TV remote Menu button to open filters
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            openFilterBottomSheet()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /**
     * Open filter bottom sheet
     */
    private fun openFilterBottomSheet() {
        lifecycleScope.launch {
            val currentFilter = viewModel.currentFilter.value

            val bottomSheet = FilterBottomSheet(currentFilter) { newFilter ->
                viewModel.updateFilter(newFilter)
                Toast.makeText(this@MainActivity, "Filtri applicati", Toast.LENGTH_SHORT).show()
            }

            bottomSheet.show(supportFragmentManager, "FilterBottomSheet")
        }
    }
}
