package com.bump.visualizer

import kotlin.math.*

enum class MaskMode { NONE, CINEMA, CIRCLE, ROUNDED }

data class MotionSettings(
    val trailCount: Int = 5,
    val trailSpacingMs: Long = 42L,
    val trailDecay: Float = 0.72f,
    val depthCurve: Float = 0.24f,
    val perspective: Float = 1050f,
    val fisheyeStrength: Float = 0.18f,
    val fisheyeFocusX: Float = 0.5f,
    val fisheyeFocusY: Float = 0.5f,
    val vignette: Float = 0.34f,
    val edgeFade: Float = 0.12f,
    val maskMode: MaskMode = MaskMode.NONE,
    val maskAmount: Float = 0.0f
)

object MotionMath {
    fun trailAlpha(base: Float, index: Int, decay: Float): Int =
        (base * decay.pow(index + 1)).toInt().coerceIn(0, 255)

    fun curvedDepth(depthIndex: Int, step: Float, curve: Float): Float {
        val d = depthIndex.toFloat()
        return d * step + d * abs(d) * step * curve * 0.035f
    }

    fun easeOutCubic(t: Float): Float {
        val x = 1f - t.coerceIn(0f, 1f)
        return 1f - x * x * x
    }
}
