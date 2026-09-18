package com.bump.visualizer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.max
import kotlin.math.min

class WordTimelineView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var words: List<LyricWord> = emptyList()
    private var selected = -1
    private var durationMs = 60000L
    private var cursorMs = 0L
    private var pxPerMs = 0.08f
    private var dragging = false
    private var dragMode = 0
    private var downX = 0f
    private var originalStart = 0L
    private var originalEnd = 0L
    private var originalSelected = -1
    var onWordSelected: ((Int) -> Unit)? = null
    var onWordChanged: ((Int, Long, Long) -> Unit)? = null
    var onSeek: ((Long) -> Unit)? = null

    fun setWords(value: List<LyricWord>) { words = value.map { it.copy() }; invalidate() }
    fun setSelected(index: Int) { selected = index; invalidate() }
    fun setDuration(ms: Long) { durationMs = max(1000L, ms); invalidate() }
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
        paint.color = Color.rgb(55, 55, 55)
        for (t in 0L..durationMs step 1000L) {
            val x = timeToX(t)
            paint.strokeWidth = if (t % 5000L == 0L) 2f else 1f
            c.drawLine(x, 0f, x, height.toFloat(), paint)
            if (t % 5000L == 0L) {
                paint.color = Color.rgb(130, 130, 130)
                paint.textSize = 18f
                c.drawText(format(t), x + 5f, 22f, paint)
                paint.color = Color.rgb(55, 55, 55)
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
                if (originalSelected >= 0) {
                    selected = originalSelected
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
                } else {
                    cursorMs = xToTime(e.x)
                    onSeek?.invoke(cursorMs)
                    dragging = false
                }
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> if (dragging && selected >= 0) {
                val delta = xToTime(e.x) - xToTime(downX)
                var start = originalStart
                var end = originalEnd
                when (dragMode) {
                    1 -> start = min(originalStart + delta, originalEnd - 80L).coerceAtLeast(0L)
                    2 -> end = max(originalEnd + delta, originalStart + 80L).coerceAtMost(durationMs)
                    else -> {
                        val d = delta
                        val len = originalEnd - originalStart
                        start = (originalStart + d).coerceIn(0L, durationMs - len)
                        end = start + len
                    }
                }
                onWordChanged?.invoke(selected, start, end)
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                return true
            }
        }
        return true
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
