package com.musicplayer.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.musicplayer.app.R
import com.musicplayer.app.model.Song

class SongAdapter(
    private val onSongClick: (Song, Int) -> Unit,
    private val onSongLongClick: (Song, Int) -> Unit = { _, _ -> }
) : ListAdapter<Song, SongAdapter.SongViewHolder>(SongDiffCallback()) {

    private var currentPlayingIndex: Int = -1

    fun setCurrentPlaying(index: Int) {
        val old = currentPlayingIndex
        currentPlayingIndex = index
        if (old >= 0) notifyItemChanged(old)
        if (index >= 0) notifyItemChanged(index)
    }

    inner class SongViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val card: MaterialCardView = itemView.findViewById(R.id.card_song)
        val ivAlbumArt: ImageView = itemView.findViewById(R.id.iv_album_art)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_song_title)
        val tvArtist: TextView = itemView.findViewById(R.id.tv_song_artist)
        val tvDuration: TextView = itemView.findViewById(R.id.tv_song_duration)
        val ivFlacBadge: TextView = itemView.findViewById(R.id.tv_flac_badge)
        val ivPlaying: ImageView = itemView.findViewById(R.id.iv_playing_indicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_song, parent, false)
        return SongViewHolder(view)
    }

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = getItem(position)
        val isPlaying = position == currentPlayingIndex

        holder.tvTitle.text = song.title
        holder.tvArtist.text = song.artist
        holder.tvDuration.text = song.durationFormatted()
        holder.ivFlacBadge.visibility = if (song.isFlac()) View.VISIBLE else View.GONE
        holder.ivPlaying.visibility = if (isPlaying) View.VISIBLE else View.GONE

        holder.card.isChecked = isPlaying

        holder.card.setOnClickListener {
            onSongClick(song, holder.adapterPosition)
        }
        holder.card.setOnLongClickListener {
            onSongLongClick(song, holder.adapterPosition)
            true
        }
    }

    class SongDiffCallback : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem == newItem
    }
}
