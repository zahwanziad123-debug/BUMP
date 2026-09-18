package com.bump.visualizer

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

class AudioPlayerController(context: Context) {
    private val player = ExoPlayer.Builder(context.applicationContext).build()

    val isPlaying: Boolean
        get() = player.isPlaying

    val positionMs: Long
        get() = player.currentPosition.coerceAtLeast(0L)

    val durationMs: Long
        get() = player.duration.coerceAtLeast(0L)

    fun open(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
    }

    fun toggle() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun release() {
        player.release()
    }
}
