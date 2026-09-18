package com.bump.visualizer

data class LyricWord(
    val text: String,
    val startMs: Long,
    var endMs: Long,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f,
    var rotationX: Float = 0f,
    var rotationY: Float = 0f,
    var scale: Float = 1f
)
