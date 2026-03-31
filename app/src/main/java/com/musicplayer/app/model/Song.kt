package com.musicplayer.app.model

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,       // milliseconds
    val path: String,
    val albumArtUri: String?,
    val size: Long,
    val dateAdded: Long
) {
    fun durationFormatted(): String {
        val totalSeconds = duration / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun isFlac(): Boolean = path.endsWith(".flac", ignoreCase = true)
}
