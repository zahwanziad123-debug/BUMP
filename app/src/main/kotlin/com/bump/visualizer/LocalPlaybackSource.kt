package com.bump.visualizer

import android.content.Context
import android.net.Uri

class LocalPlaybackSource(context: Context) : PlaybackSource {
    private val controller = AudioPlayerController(context)

    override val provider: String = "Local"
    override val isConnected: Boolean = true
    override val isPlaying: Boolean get() = controller.isPlaying
    override val positionMs: Long get() = controller.positionMs
    override val durationMs: Long get() = controller.durationMs
    override val title: String = ""
    override val artist: String = ""

    fun open(uri: Uri) = controller.open(uri)
    override fun play() { if (!controller.isPlaying) controller.toggle() }
    override fun pause() { if (controller.isPlaying) controller.toggle() }
    override fun seekTo(positionMs: Long) = controller.seekTo(positionMs)
    override fun disconnect() = controller.release()
}
