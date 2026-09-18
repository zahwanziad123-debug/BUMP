package com.bump.visualizer

import kotlin.math.abs
import kotlin.math.pow

data class MotionSettings(
    val depthCurve: Float = 1.0f,
    val perspective: Float = 1050f,
    val travel: Float = 85f,
    val sway: Float = 24f,
    val drift: Float = 30f,
    val tilt: Float = 30f,
    val depthTilt: Float = 3.5f,
    val trailCount: Int = 3,
    val trailSpacing: Float = 18f,
    val trailSpacingMs: Long = 42L,
    val trailDecay: Float = 0.72f,
    val fisheyeStrength: Float = 0.18f,
    val fisheyeFocusX: Float = 0.5f,
    val fisheyeFocusY: Float = 0.5f,
    val vignette: Float = 0.34f,
    val edgeFade: Float = 0.12f,
    val maskAmount: Float = 0.0f
)

enum class MaskMode { NONE, CINEMA, CIRCLE, ROUNDED }

object MotionMath {
    fun curvedDepth(index: Int, step: Float, curve: Float): Float {
        val magnitude = abs(index).toFloat().pow(curve.coerceIn(0.55f, 2.2f))
        return if (index < 0) -magnitude * step else magnitude * step
    }

    fun trailAlpha(base: Float, index: Int, decay: Float): Int =
        (base * decay.pow(index + 1)).toInt().coerceIn(0, 255)

    fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun easeOutCubic(t: Float): Float {
        val x = 1f - t.coerceIn(0f, 1f)
        return 1f - x * x * x
    }
}
