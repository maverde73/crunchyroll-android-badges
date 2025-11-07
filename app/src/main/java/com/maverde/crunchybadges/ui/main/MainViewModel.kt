package com.maverde.crunchybadges.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.maverde.crunchybadges.data.local.database.AnimeDatabase
import com.maverde.crunchybadges.data.local.entities.AnimeEntity
import com.maverde.crunchybadges.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for MainActivity
 * Manages anime list from database
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AnimeDatabase.getDatabase(application)
    private val repository = AnimeRepository(database.animeDao())

    private val _animeList = MutableStateFlow<List<AnimeEntity>>(emptyList())
    val animeList: StateFlow<List<AnimeEntity>> = _animeList

    init {
        loadAnime()
    }

    /**
     * Load Italian anime from database
     */
    private fun loadAnime() {
        viewModelScope.launch {
            repository.getAllItalianAnime().collect { anime ->
                _animeList.value = anime
            }
        }
    }

    /**
     * Refresh anime list (for future pull-to-refresh)
     */
    fun refresh() {
        loadAnime()
    }
}
