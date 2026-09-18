package com.bump.visualizer

data class MotionSettings(
    val depthCurve: Float = 1.0f,
    val perspective: Float = 1050f,
    val travel: Float = 85f,
    val sway: Float = 24f,
    val drift: Float = 30f,
    val tilt: Float = 30f,
    val depthTilt: Float = 3.5f,
    val trailCount: Int = 3,
    val trailSpacing: Float = 18f
)

enum class MaskMode { NONE, CINEMA, CIRCLE, ROUNDED }

object MotionMath {
    fun curvedDepth(index: Int, step: Float, curve: Float): Float {
        val magnitude = kotlin.math.abs(index).toFloat().let { it.pow(curve.coerceIn(0.55f, 2.2f)) }
        return if (index < 0) -magnitude * step else magnitude * step
    }

    fun smooth(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
