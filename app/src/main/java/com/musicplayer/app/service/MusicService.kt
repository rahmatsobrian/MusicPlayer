package com.musicplayer.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.musicplayer.app.MainActivity
import com.musicplayer.app.R
import com.musicplayer.app.model.Song

class MusicService : Service() {

    companion object {
        const val CHANNEL_ID = "music_player_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_PLAY = "com.musicplayer.ACTION_PLAY"
        const val ACTION_PAUSE = "com.musicplayer.ACTION_PAUSE"
        const val ACTION_NEXT = "com.musicplayer.ACTION_NEXT"
        const val ACTION_PREV = "com.musicplayer.ACTION_PREV"
        const val ACTION_STOP = "com.musicplayer.ACTION_STOP"
    }

    inner class MusicBinder : Binder() {
        fun getService(): MusicService = this@MusicService
    }

    private val binder = MusicBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentSong: Song? = null
    private var playlist: List<Song> = emptyList()
    private var currentIndex: Int = -1
    private var isShuffleEnabled: Boolean = false
    private var repeatMode: RepeatMode = RepeatMode.NONE

    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onSongChanged: ((Song?, Int) -> Unit)? = null
    var onProgressUpdate: ((Int, Int) -> Unit)? = null
    var onPlaybackError: ((String) -> Unit)? = null

    enum class RepeatMode { NONE, ONE, ALL }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_NEXT -> playNext()
            ACTION_PREV -> playPrevious()
            ACTION_STOP -> stopSelf()
        }
        return START_NOT_STICKY
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        playlist = songs
        if (songs.isNotEmpty() && startIndex < songs.size) {
            playSong(startIndex)
        }
    }

    fun playSong(index: Int) {
        if (index < 0 || index >= playlist.size) return
        currentIndex = index
        val song = playlist[index]
        currentSong = song

        mediaPlayer?.release()
        mediaPlayer = null

        try {
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
                setDataSource(song.path)
                setOnPreparedListener { mp ->
                    mp.start()
                    onPlaybackStateChanged?.invoke(true)
                    onSongChanged?.invoke(song, index)
                    updateNotification(song, true)
                }
                setOnCompletionListener {
                    handleCompletion()
                }
                setOnErrorListener { _, what, extra ->
                    onPlaybackError?.invoke("Playback error: what=$what extra=$extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            onPlaybackError?.invoke("Failed to play: ${e.message}")
        }
    }

    private fun handleCompletion() {
        when (repeatMode) {
            RepeatMode.ONE -> playSong(currentIndex)
            RepeatMode.ALL -> {
                val next = (currentIndex + 1) % playlist.size
                playSong(next)
            }
            RepeatMode.NONE -> {
                if (currentIndex < playlist.size - 1) {
                    playNext()
                } else {
                    onPlaybackStateChanged?.invoke(false)
                    updateNotification(currentSong, false)
                }
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                onPlaybackStateChanged?.invoke(false)
                currentSong?.let { song -> updateNotification(song, false) }
            }
        }
    }

    fun resumePlayback() {
        mediaPlayer?.let {
            if (!it.isPlaying) {
                it.start()
                onPlaybackStateChanged?.invoke(true)
                currentSong?.let { song -> updateNotification(song, true) }
            }
        }
    }

    fun playNext() {
        if (playlist.isEmpty()) return
        val next = if (isShuffleEnabled) {
            (0 until playlist.size).random()
        } else {
            (currentIndex + 1) % playlist.size
        }
        playSong(next)
    }

    fun playPrevious() {
        if (playlist.isEmpty()) return
        val prev = if (currentIndex <= 0) playlist.size - 1 else currentIndex - 1
        playSong(prev)
    }

    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun getCurrentSong(): Song? = currentSong

    fun getCurrentIndex(): Int = currentIndex

    fun toggleShuffle(): Boolean {
        isShuffleEnabled = !isShuffleEnabled
        return isShuffleEnabled
    }

    fun cycleRepeatMode(): RepeatMode {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        return repeatMode
    }

    fun getRepeatMode(): RepeatMode = repeatMode

    fun isShuffleOn(): Boolean = isShuffleEnabled

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Music playback controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification(song: Song?, isPlaying: Boolean) {
        val notification = buildNotification(song, isPlaying)
        if (isPlaying) {
            startForeground(NOTIFICATION_ID, notification)
        } else {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(song: Song?, isPlaying: Boolean): Notification {
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val mainPending = PendingIntent.getActivity(this, 0, mainIntent, pendingFlags)

        fun actionIntent(action: String, reqCode: Int): PendingIntent {
            val i = Intent(this, MusicService::class.java).apply { this.action = action }
            return PendingIntent.getService(this, reqCode, i, pendingFlags)
        }

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        val playPauseAction = if (isPlaying) ACTION_PAUSE else ACTION_PLAY

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(song?.title ?: "Music Player")
            .setContentText(song?.artist ?: "")
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(mainPending)
            .addAction(R.drawable.ic_skip_previous, "Previous", actionIntent(ACTION_PREV, 1))
            .addAction(playPauseIcon, if (isPlaying) "Pause" else "Play", actionIntent(playPauseAction, 2))
            .addAction(R.drawable.ic_skip_next, "Next", actionIntent(ACTION_NEXT, 3))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
