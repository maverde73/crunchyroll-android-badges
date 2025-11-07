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
import com.maverde.crunchybadges.data.local.entities.AnimeEntity

/**
 * Adapter for displaying anime list in RecyclerView
 */
class AnimeListAdapter(
    private val onAnimeClick: (AnimeEntity) -> Unit
) : ListAdapter<AnimeEntity, AnimeListAdapter.AnimeViewHolder>(AnimeDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnimeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_anime_card, parent, false)
        return AnimeViewHolder(view, onAnimeClick)
    }

    override fun onBindViewHolder(holder: AnimeViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AnimeViewHolder(
        itemView: View,
        private val onAnimeClick: (AnimeEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val posterImage: ImageView = itemView.findViewById(R.id.posterImage)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val episodeCountText: TextView = itemView.findViewById(R.id.episodeCountText)
        private val ratingText: TextView = itemView.findViewById(R.id.ratingText)

        fun bind(anime: AnimeEntity) {
            titleText.text = anime.title
            episodeCountText.text = if (anime.episodeCount > 1) {
                "${anime.episodeCount} episodi"
            } else {
                "${anime.episodeCount} episodio"
            }
            ratingText.text = String.format("%.1f", anime.rating)

            // Load poster image with Coil
            posterImage.load(anime.posterTallUrl) {
                crossfade(true)
                placeholder(R.drawable.icon48)
                error(R.drawable.icon48)
            }

            // Click listener
            itemView.setOnClickListener {
                onAnimeClick(anime)
            }
        }
    }

    /**
     * DiffUtil callback for efficient list updates
     */
    class AnimeDiffCallback : DiffUtil.ItemCallback<AnimeEntity>() {
        override fun areItemsTheSame(oldItem: AnimeEntity, newItem: AnimeEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AnimeEntity, newItem: AnimeEntity): Boolean {
            return oldItem == newItem
        }
    }
}
