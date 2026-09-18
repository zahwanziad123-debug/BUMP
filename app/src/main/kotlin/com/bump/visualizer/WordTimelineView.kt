package com.bump.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class WordTimelineView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var words: List<LyricWord> = emptyList()
    private var selected = -1
    private var selectedKeyframe = -1
    private var durationMs = 60000L
    private var cursorMs = 0L
    private var pxPerMs = 0.08f
    private var dragging = false
    private var dragMode = 0
    private var downX = 0f
    private var originalStart = 0L
    private var originalEnd = 0L
    private var originalKeyframeTime = 0L

    var onWordSelected: ((Int) -> Unit)? = null
    var onWordChanged: ((Int, Long, Long) -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null
    var onWordEditStarted: (() -> Unit)? = null
    var onWordEditFinished: (() -> Unit)? = null
    var onKeyframeSelected: ((Int, Int) -> Unit)? = null
    var onKeyframeChanged: ((Int, Int, Long) -> Unit)? = null
    var onKeyframeEditStarted: (() -> Unit)? = null
    var onKeyframeEditFinished: (() -> Unit)? = null

    fun setWords(value: List<LyricWord>) {
        words = value.map { it.copy(keyframes = it.keyframes.map { k -> k.copy() }.toMutableList()) }
        if (selected !in words.indices) selectedKeyframe = -1
        if (selected >= 0 && selected < words.size && selectedKeyframe !in words[selected].keyframes.indices) selectedKeyframe = -1
        invalidate()
    }

    fun setSelected(index: Int) {
        selected = index
        selectedKeyframe = -1
        invalidate()
    }

    fun setSelectedKeyframe(index: Int) {
        selectedKeyframe = index
        invalidate()
    }

    fun setDuration(ms: Long) { durationMs = max(1000L, ms); requestLayout(); invalidate() }
    fun setCursor(ms: Long) { cursorMs = ms.coerceIn(0L, durationMs); invalidate() }

    fun zoomIn() { setZoom(pxPerMs * 1.5f) }
    fun zoomOut() { setZoom(pxPerMs / 1.5f) }
    fun setZoom(value: Float) {
        pxPerMs = value.coerceIn(0.02f, 0.32f)
        requestLayout()
        invalidate()
    }

    fun contentWidth(): Int = max(5000f, durationMs * pxPerMs + 120f).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(contentWidth(), 170)
    }

    private fun timeToX(ms: Long): Float = ms * pxPerMs
    private fun xToTime(x: Float): Long = (x / pxPerMs).toLong().coerceIn(0L, durationMs)

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        c.drawColor(Color.rgb(8, 8, 8))
        paint.style = Paint.Style.FILL

        for (t in 0L..durationMs step 1000L) {
            val x = timeToX(t)
            paint.color = if (t % 5000L == 0L) Color.rgb(85, 85, 85) else Color.rgb(38, 38, 38)
            paint.strokeWidth = if (t % 5000L == 0L) 2f else 1f
            c.drawLine(x, 0f, x, height.toFloat(), paint)
            if (t % 5000L == 0L) {
                paint.color = Color.rgb(130, 130, 130)
                paint.textSize = 18f
                c.drawText(format(t), x + 5f, 22f, paint)
            }
        }

        words.forEachIndexed { i, w ->
            val y = 36f + (i % 3) * 42f
            val left = timeToX(w.startMs)
            val right = max(left + 12f, timeToX(w.endMs))
            paint.color = if (i == selected) Color.WHITE else Color.rgb(105, 105, 105)
            c.drawRoundRect(left, y, right, y + 30f, 7f, 7f, paint)

            paint.color = if (i == selected) Color.BLACK else Color.WHITE
            paint.textSize = 14f
            val label = if (w.text.length > 16) w.text.take(15) + "…" else w.text
            c.drawText(label, left + 7f, y + 20f, paint)

            w.keyframes.forEachIndexed { kIndex, k ->
                val kx = timeToX(k.timeMs)
                val ky = y + 15f
                val size = if (i == selected && kIndex == selectedKeyframe) 8f else 6f
                paint.color = when {
                    i == selected && kIndex == selectedKeyframe -> Color.WHITE
                    i == selected -> Color.rgb(220, 220, 220)
                    else -> Color.rgb(145, 145, 145)
                }
                val path = Path().apply {
                    moveTo(kx, ky - size)
                    lineTo(kx + size, ky)
                    lineTo(kx, ky + size)
                    lineTo(kx - size, ky)
                    close()
                }
                c.drawPath(path, paint)

                if (i == selected && kIndex == selectedKeyframe) {
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = Color.WHITE
                    c.drawCircle(kx, ky, size + 4f, paint)
                    paint.style = Paint.Style.FILL
                }
            }
        }

        paint.color = Color.WHITE
        paint.strokeWidth = 2f
        val cursorX = timeToX(cursorMs)
        c.drawLine(cursorX, 0f, cursorX, height.toFloat(), paint)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.x
                originalSelected = hitWord(e.x, e.y)

                val keyframeHit = hitKeyframe(e.x, e.y)
                if (keyframeHit != null) {
                    selected = keyframeHit.first
                    selectedKeyframe = keyframeHit.second
                    onWordSelected?.invoke(selected)
                    onKeyframeSelected?.invoke(selected, selectedKeyframe)
                    originalKeyframeTime = words[selected].keyframes[selectedKeyframe].timeMs
                    dragging = true
                    dragMode = 4
                    onKeyframeEditStarted?.invoke()
                    invalidate()
                    return true
                }

                if (originalSelected >= 0) {
                    selected = originalSelected
                    selectedKeyframe = -1
                    onWordSelected?.invoke(selected)
                    val w = words[selected]
                    originalStart = w.startMs
                    originalEnd = w.endMs
                    val left = timeToX(w.startMs)
                    val right = timeToX(w.endMs)
                    dragMode = when {
                        e.x - left < 22f -> 1
                        right - e.x < 22f -> 2
                        else -> 3
                    }
                    dragging = true
                    onWordEditStarted?.invoke()
                } else {
                    selectedKeyframe = -1
                    cursorMs = xToTime(e.x)
                    onSeek?.invoke(cursorMs)
                    dragging = false
                }
                invalidate()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return true

                if (dragMode == 4 && selected in words.indices && selectedKeyframe in words[selected].keyframes.indices) {
                    val deltaMs = xToTime(e.x) - xToTime(downX)
                    val w = words[selected]
                    val k = w.keyframes[selectedKeyframe]
                    val previous = if (selectedKeyframe > 0) w.keyframes[selectedKeyframe - 1].timeMs else 0L
                    val next = if (selectedKeyframe < w.keyframes.lastIndex) w.keyframes[selectedKeyframe + 1].timeMs else durationMs
                    val newTime = (originalKeyframeTime + deltaMs).coerceIn(previous + 1L, next - 1L)
                    if (newTime != k.timeMs) {
                        w.keyframes[selectedKeyframe] = k.copy(timeMs = newTime)
                        onKeyframeChanged?.invoke(selected, selectedKeyframe, newTime)
                        invalidate()
                    }
                    return true
                }

                if (selected >= 0) {
                    val delta = xToTime(e.x) - xToTime(downX)
                    var start = originalStart
                    var end = originalEnd
                    when (dragMode) {
                        1 -> start = min(originalStart + delta, originalEnd - 80L).coerceAtLeast(0L)
                        2 -> end = max(originalEnd + delta, originalStart + 80L).coerceAtMost(durationMs)
                        else -> {
                            val len = originalEnd - originalStart
                            start = (originalStart + delta).coerceIn(0L, durationMs - len)
                            end = start + len
                        }
                    }
                    onWordChanged?.invoke(selected, start, end)
                    invalidate()
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    if (dragMode == 4) onKeyframeEditFinished?.invoke()
                    else onWordEditFinished?.invoke()
                }
                dragging = false
                return true
            }
        }
        return true
    }

    private fun hitKeyframe(x: Float, y: Float): Pair<Int, Int>? {
        for (i in words.indices.reversed()) {
            val row = 36f + (i % 3) * 42f
            if (y !in (row - 8f)..(row + 38f)) continue
            words[i].keyframes.forEachIndexed { kIndex, k ->
                if (kotlin.math.abs(x - timeToX(k.timeMs)) <= 14f && kotlin.math.abs(y - (row + 15f)) <= 18f) {
                    return i to kIndex
                }
            }
        }
        return null
    }

    private fun hitWord(x: Float, y: Float): Int {
        for (i in words.indices.reversed()) {
            val row = 36f + (i % 3) * 42f
            val left = timeToX(words[i].startMs)
            val right = max(left + 12f, timeToX(words[i].endMs))
            if (x in left..right && y in row..(row + 30f)) return i
        }
        return -1
    }

    private fun format(ms: Long): String {
        val s = ms / 1000L
        return "%02d:%02d".format(s / 60L, s % 60L)
    }
}
