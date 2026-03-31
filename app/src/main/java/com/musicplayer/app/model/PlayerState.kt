package com.musicplayer.app.model

sealed class PlayerState {
    object Idle : PlayerState()
    object Loading : PlayerState()
    data class Playing(val song: Song, val position: Int) : PlayerState()
    data class Paused(val song: Song, val position: Int) : PlayerState()
    data class Error(val message: String) : PlayerState()
}
