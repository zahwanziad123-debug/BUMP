package com.bump.visualizer

/**
 * Common playback clock used by BUMP's lyric/visual rendering engine.
 * Streaming providers expose only the playback state that their APIs permit.
 */
interface PlaybackSource {
    val provider: String
    val isConnected: Boolean
    val isPlaying: Boolean
    val positionMs: Long
    val durationMs: Long
    val title: String
    val artist: String

    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun disconnect()
}
