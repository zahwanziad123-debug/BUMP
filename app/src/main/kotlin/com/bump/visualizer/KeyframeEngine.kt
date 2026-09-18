package com.bump.visualizer

data class LyricKeyframe(
    val timeMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val rotationX: Float,
    val rotationY: Float,
    val scale: Float,
    val opacity: Float = 1f
)

data class AnimatedWordState(
    val x: Float,
    val y: Float,
    val z: Float,
    val rotationX: Float,
    val rotationY: Float,
    val scale: Float,
    val opacity: Float
)

object KeyframeEngine {
    fun evaluate(word: LyricWord, timeMs: Long): AnimatedWordState {
        val frames = word.keyframes.sortedBy { it.timeMs }
        if (frames.isEmpty()) {
            return AnimatedWordState(
                word.x, word.y, word.z, word.rotationX, word.rotationY, word.scale, 1f
            )
        }

        if (timeMs <= frames.first().timeMs) return frames.first().state()
        if (timeMs >= frames.last().timeMs) return frames.last().state()

        val right = frames.indexOfFirst { it.timeMs >= timeMs }
        val a = frames[right - 1]
        val b = frames[right]
        val span = (b.timeMs - a.timeMs).coerceAtLeast(1L)
        val t = ((timeMs - a.timeMs).toFloat() / span).coerceIn(0f, 1f)
        val eased = t * t * (3f - 2f * t)

        return AnimatedWordState(
            lerp(a.x, b.x, eased),
            lerp(a.y, b.y, eased),
            lerp(a.z, b.z, eased),
            lerp(a.rotationX, b.rotationX, eased),
            lerp(a.rotationY, b.rotationY, eased),
            lerp(a.scale, b.scale, eased),
            lerp(a.opacity, b.opacity, eased)
        )
    }

    private fun LyricKeyframe.state() = AnimatedWordState(
        x, y, z, rotationX, rotationY, scale, opacity
    )

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
