package com.maverde.crunchybadges

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        adapter = AnimeListAdapter { anime ->
            // TODO: Open DetailActivity or Crunchyroll app
            Toast.makeText(
                this,
                "Cliccato: ${anime.title}",
                Toast.LENGTH_SHORT
            ).show()
        }

        recyclerView.adapter = adapter
        recyclerView.layoutManager = GridLayoutManager(this, getGridSpanCount())

        // Initialize ViewModel
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Observe anime list
        lifecycleScope.launch {
            viewModel.animeList.collect { animeList ->
                if (animeList.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyText.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyText.visibility = View.GONE
                    adapter.submitList(animeList)
                }
            }
        }
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
}
