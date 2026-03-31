package com.musicplayer.app

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.musicplayer.app.databinding.ActivityMainBinding
import com.musicplayer.app.model.Song
import com.musicplayer.app.service.MusicService
import com.musicplayer.app.ui.SongAdapter
import com.musicplayer.app.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var musicService: MusicService? = null
    private var isBound = false
    private lateinit var songAdapter: SongAdapter
    private var allSongs: List<Song> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<*>
    private var isSeekBarTracking = false

    private val progressRunnable = object : Runnable {
        override fun run() {
            updateProgress()
            handler.postDelayed(this, 500)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MusicService.MusicBinder
            musicService = binder.getService().also { svc ->
                isBound = true
                setupServiceCallbacks(svc)
                updateUIFromService(svc)
                handler.post(progressRunnable)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isBound = false
            musicService = null
            handler.removeCallbacks(progressRunnable)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            loadMusic()
        } else {
            Snackbar.make(
                binding.root,
                getString(R.string.permission_denied_message),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.retry)) {
                checkAndRequestPermissions()
            }.show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupBottomSheet()
        setupPlayerControls()
        checkAndRequestPermissions()
        bindMusicService()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.app_name)
    }

    private fun setupRecyclerView() {
        songAdapter = SongAdapter(
            onSongClick = { song, index ->
                musicService?.let { svc ->
                    val currentList = songAdapter.currentList
                    svc.setPlaylist(currentList, index)
                    expandBottomSheet()
                }
            },
            onSongLongClick = { song, _ ->
                Toast.makeText(
                    this,
                    "${song.title}\n${song.artist}\n${song.durationFormatted()}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
        binding.recyclerViewSongs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = songAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomSheetPlayer)
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (newState) {
                    BottomSheetBehavior.STATE_EXPANDED -> {
                        binding.miniPlayer.visibility = View.GONE
                    }
                    BottomSheetBehavior.STATE_COLLAPSED,
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        val hasSong = musicService?.getCurrentSong() != null
                        binding.miniPlayer.visibility = if (hasSong) View.VISIBLE else View.GONE
                    }
                }
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) {}
        })

        binding.miniPlayer.setOnClickListener {
            expandBottomSheet()
        }
    }

    private fun expandBottomSheet() {
        bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
        binding.miniPlayer.visibility = View.GONE
    }

    private fun setupPlayerControls() {
        binding.apply {
            btnPlayPause.setOnClickListener {
                musicService?.let { svc ->
                    if (svc.isPlaying()) svc.pausePlayback() else svc.resumePlayback()
                }
            }
            btnNext.setOnClickListener { musicService?.playNext() }
            btnPrevious.setOnClickListener { musicService?.playPrevious() }
            btnShuffle.setOnClickListener {
                musicService?.let { svc ->
                    val on = svc.toggleShuffle()
                    btnShuffle.isChecked = on
                }
            }
            btnRepeat.setOnClickListener {
                musicService?.let { svc ->
                    val mode = svc.cycleRepeatMode()
                    updateRepeatButton(mode)
                }
            }
            miniPlayPause.setOnClickListener {
                musicService?.let { svc ->
                    if (svc.isPlaying()) svc.pausePlayback() else svc.resumePlayback()
                }
            }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        tvCurrentTime.text = formatDuration(progress.toLong())
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { isSeekBarTracking = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    isSeekBarTracking = false
                    musicService?.seekTo(sb?.progress ?: 0)
                }
            })
        }
    }

    private fun setupServiceCallbacks(svc: MusicService) {
        svc.onPlaybackStateChanged = { isPlaying ->
            runOnUiThread { updatePlayPauseButton(isPlaying) }
        }
        svc.onSongChanged = { song, index ->
            runOnUiThread {
                song?.let { updateNowPlaying(it) }
                songAdapter.setCurrentPlaying(index)
            }
        }
        svc.onPlaybackError = { msg ->
            runOnUiThread {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateUIFromService(svc: MusicService) {
        val song = svc.getCurrentSong() ?: return
        updateNowPlaying(song)
        updatePlayPauseButton(svc.isPlaying())
        songAdapter.setCurrentPlaying(svc.getCurrentIndex())
        updateRepeatButton(svc.getRepeatMode())
        binding.btnShuffle.isChecked = svc.isShuffleOn()
        if (song != null) {
            binding.miniPlayer.visibility = View.VISIBLE
        }
    }

    private fun updateNowPlaying(song: Song) {
        binding.apply {
            tvSongTitle.text = song.title
            tvSongArtist.text = song.artist
            tvSongAlbum.text = song.album
            miniSongTitle.text = song.title
            miniSongArtist.text = song.artist
            tvFlacBadge.visibility = if (song.isFlac()) View.VISIBLE else View.GONE
            miniPlayer.visibility = View.VISIBLE
        }
    }

    private fun updatePlayPauseButton(isPlaying: Boolean) {
        binding.btnPlayPause.setIconResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
        binding.miniPlayPause.setImageResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }

    private fun updateRepeatButton(mode: MusicService.RepeatMode) {
        val icon = when (mode) {
            MusicService.RepeatMode.NONE -> R.drawable.ic_repeat
            MusicService.RepeatMode.ALL -> R.drawable.ic_repeat
            MusicService.RepeatMode.ONE -> R.drawable.ic_repeat_one
        }
        binding.btnRepeat.setIconResource(icon)
        binding.btnRepeat.isChecked = mode != MusicService.RepeatMode.NONE
    }

    private fun updateProgress() {
        val svc = musicService ?: return
        val duration = svc.getDuration()
        val position = svc.getCurrentPosition()
        if (duration > 0 && !isSeekBarTracking) {
            binding.seekBar.max = duration
            binding.seekBar.progress = position
            binding.tvCurrentTime.text = formatDuration(position.toLong())
            binding.tvTotalTime.text = formatDuration(duration.toLong())
        }
    }

    private fun formatDuration(millis: Long): String {
        val totalSeconds = millis / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (permissions.isEmpty()) {
            loadMusic()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    private fun loadMusic() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val songs = withContext(Dispatchers.IO) {
                MusicScanner.scanMusic(this@MainActivity)
            }
            allSongs = songs
            songAdapter.submitList(songs)
            binding.progressBar.visibility = View.GONE
            if (songs.isEmpty()) {
                binding.tvEmpty.visibility = View.VISIBLE
            }
            binding.tvSongCount.text = getString(R.string.song_count, songs.size)
        }
    }

    private fun bindMusicService() {
        val intent = Intent(this, MusicService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as? SearchView
        searchView?.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                filterSongs(newText.orEmpty())
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort_title -> {
                songAdapter.submitList(allSongs.sortedBy { it.title })
                true
            }
            R.id.action_sort_artist -> {
                songAdapter.submitList(allSongs.sortedBy { it.artist })
                true
            }
            R.id.action_sort_duration -> {
                songAdapter.submitList(allSongs.sortedByDescending { it.duration })
                true
            }
            R.id.action_filter_flac -> {
                val flac = allSongs.filter { it.isFlac() }
                songAdapter.submitList(flac)
                Toast.makeText(this, "${flac.size} FLAC files found", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_show_all -> {
                songAdapter.submitList(allSongs)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun filterSongs(query: String) {
        val filtered = if (query.isBlank()) {
            allSongs
        } else {
            allSongs.filter {
                it.title.contains(query, ignoreCase = true) ||
                        it.artist.contains(query, ignoreCase = true) ||
                        it.album.contains(query, ignoreCase = true)
            }
        }
        songAdapter.submitList(filtered)
    }

    override fun onBackPressed() {
        if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(progressRunnable)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }
}
