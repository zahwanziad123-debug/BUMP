package com.bump.visualizer

fun LyricVisualizerView.updateSelectedKeyframe(
    timeMs: Long? = null,
    x: Float? = null,
    y: Float? = null,
    z: Float? = null,
    rotationX: Float? = null,
    rotationY: Float? = null,
    scale: Float? = null,
    opacity: Float? = null
) {
    val word = selectedWord() ?: return
    val index = selectedKeyframeIndex()
    val old = word.keyframes.getOrNull(index) ?: return
    val newTime = (timeMs ?: old.timeMs).coerceAtLeast(0L)
    word.keyframes[index] = old.copy(
        timeMs = newTime,
        x = x ?: old.x,
        y = y ?: old.y,
        z = z ?: old.z,
        rotationX = rotationX ?: old.rotationX,
        rotationY = rotationY ?: old.rotationY,
        scale = (scale ?: old.scale).coerceIn(0.05f, 5f),
        opacity = (opacity ?: old.opacity).coerceIn(0f, 1f)
    )
    word.keyframes.sortBy { it.timeMs }
    selectKeyframe(word.keyframes.indexOfFirst { it.timeMs == newTime })
}

fun LyricVisualizerView.removeSelectedKeyframe() {
    val word = selectedWord() ?: return
    val index = selectedKeyframeIndex()
    if (index !in word.keyframes.indices) return
    word.keyframes.removeAt(index)
    selectKeyframe(-1)
}
