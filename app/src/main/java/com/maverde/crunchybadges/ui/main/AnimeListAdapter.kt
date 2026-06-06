package com.maverde.crunchybadges.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.maverde.crunchybadges.R
import com.maverde.crunchybadges.data.local.entities.SeriesWithAllData
import com.maverde.crunchybadges.utils.LocaleHelper

/**
 * Adapter for displaying series list in RecyclerView
 * V2: Updated to use SeriesWithAllData (normalized schema)
 */
class AnimeListAdapter(
    private val onSeriesClick: (SeriesWithAllData) -> Unit
) : ListAdapter<SeriesWithAllData, AnimeListAdapter.SeriesViewHolder>(SeriesDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SeriesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_anime_card, parent, false)
        return SeriesViewHolder(view, onSeriesClick)
    }

    override fun onBindViewHolder(holder: SeriesViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class SeriesViewHolder(
        itemView: View,
        private val onSeriesClick: (SeriesWithAllData) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val episodeCountText: TextView = itemView.findViewById(R.id.episodeCountText)
        private val ratingContainer: View = itemView.findViewById(R.id.ratingContainer)
        private val ratingText: TextView = itemView.findViewById(R.id.ratingText)
        private val maturityRatingText: TextView = itemView.findViewById(R.id.maturityRatingText)
        private val languageBadgeText: TextView = itemView.findViewById(R.id.languageBadgeText)
        private val badgeCrunchyroll: TextView = itemView.findViewById(R.id.badgeCrunchyroll)
        private val badgeAnimeGeneration: TextView = itemView.findViewById(R.id.badgeAnimeGeneration)

        init {
            // Add focus change listener for visual feedback
            itemView.setOnFocusChangeListener { view, hasFocus ->
                val scale = if (hasFocus) 1.08f else 1.0f
                val elevation = if (hasFocus) 12f else 4f

                view.animate()
                    .scaleX(scale)
                    .scaleY(scale)
                    .setDuration(200)
                    .start()

                // Update card elevation
                if (view is androidx.cardview.widget.CardView) {
                    view.cardElevation = elevation * view.resources.displayMetrics.density
                }
            }
        }

        fun bind(seriesData: SeriesWithAllData) {
            // Use series and metadata from the normalized structure
            val series = seriesData.series
            val metadata = seriesData.metadata
            val rating = seriesData.rating

            titleText.text = series.title

            // Episode count from metadata
            val episodeCount = metadata?.episodeCount ?: 0
            episodeCountText.text = if (episodeCount > 1) {
                "$episodeCount episodi"
            } else {
                "$episodeCount episodio"
            }

            // Show rating only if > 0 (0 means no rating available)
            val ratingAverage = rating?.average ?: 0.0
            if (ratingAverage > 0) {
                ratingContainer.visibility = View.VISIBLE
                ratingText.text = String.format("%.1f", ratingAverage)
            } else {
                ratingContainer.visibility = View.GONE
            }

            // Maturity rating - use helper method from SeriesWithAllData
            val maturityRating = seriesData.getMaturityRating()
            if (maturityRating.isNotEmpty()) {
                maturityRatingText.visibility = View.VISIBLE
                maturityRatingText.text = maturityRating
            } else {
                maturityRatingText.visibility = View.GONE
            }

            // Language badge - show flag emoji only if series has audio in system locale
            val systemLocale = LocaleHelper.getSystemAudioLocale(itemView.context)
            if (seriesData.hasAudioLocale(systemLocale)) {
                val flagEmoji = LocaleHelper.getFlagEmoji(systemLocale)
                if (flagEmoji != null) {
                    languageBadgeText.visibility = View.VISIBLE
                    languageBadgeText.text = flagEmoji
                } else {
                    languageBadgeText.visibility = View.GONE
                }
            } else {
                languageBadgeText.visibility = View.GONE
            }

            // Platform badges
            badgeCrunchyroll.visibility = if (seriesData.isOnCrunchyroll()) View.VISIBLE else View.GONE
            badgeAnimeGeneration.visibility = if (seriesData.isOnAnimeGeneration()) View.VISIBLE else View.GONE

            // Load poster image using helper method
            val posterUrl = seriesData.getPosterTallUrl()
            posterImage.load(posterUrl) {
                crossfade(true)
                placeholder(R.drawable.icon48)
                error(R.drawable.icon48)
            }

            // Click listener
            itemView.setOnClickListener {
                onSeriesClick(seriesData)
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    class SeriesDiffCallback : DiffUtil.ItemCallback<SeriesWithAllData>() {
        override fun areItemsTheSame(oldItem: SeriesWithAllData, newItem: SeriesWithAllData): Boolean {
            return oldItem.series.id == newItem.series.id
        }

        override fun areContentsTheSame(oldItem: SeriesWithAllData, newItem: SeriesWithAllData): Boolean {
            return oldItem == newItem
        }
    }
}
