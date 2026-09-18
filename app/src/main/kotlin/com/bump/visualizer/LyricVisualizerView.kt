package com.bump.visualizer

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class LyricVisualizerView(context: Context) : View(context), Choreographer.FrameCallback {
    private val words = LyricTimeline.demo()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = paint.typeface
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val camera = Camera()
    private val matrix = Matrix()
    private var timelineMs = 0L
    private var running = true
    private var lastNanos = System.nanoTime()
    private var downX = 0f

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun toggle() {
        running = !running
        lastNanos = System.nanoTime()
        if (running) Choreographer.getInstance().postFrameCallback(this)
        invalidate()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (running) {
            val delta = ((frameTimeNanos - lastNanos) / 1_000_000L).coerceIn(0L, 50L)
            timelineMs += delta
        }
        lastNanos = frameTimeNanos
        invalidate()
        if (running) Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        val cx = width / 2f
        val cy = height / 2f
        val current = words.indexOfLast { timelineMs >= it.startMs }.coerceAtLeast(0)

        for (i in (current - 8).coerceAtLeast(0)..(current + 4).coerceAtMost(words.lastIndex)) {
            val word = words[i]
            val depth = (i - current).toFloat()
            val z = depth * 150f
            val perspective = 1f / (1f + abs(z) / 900f)
            val x = cx + sin(i * 1.73f) * (95f + abs(depth) * 22f) * perspective
            val y = cy + (i - current) * 92f * perspective
            val rotY = sin(i * 0.83f) * 32f + depth * 4f
            val rotX = cos(i * 0.57f) * 14f
            val base = if (i == current) 78f else 43f
            val scale = perspective * if (i == current) 1.12f else 0.92f
            val alpha = if (i == current) 255 else (210 * perspective).toInt().coerceIn(35, 220)

            canvas.save()
            camera.save()
            camera.rotateX(rotX)
            camera.rotateY(rotY)
            camera.getMatrix(matrix)
            camera.restore()
            matrix.preTranslate(-cx, -cy)
            matrix.postTranslate(x, y)
            canvas.concat(matrix)
            canvas.scale(scale, scale, x, y)

            glow.textSize = base
            glow.color = Color.WHITE
            glow.alpha = (alpha * 0.13f).toInt()
            glow.maskFilter = BlurMaskFilter(14f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawText(word.text, x + 10f, y + 10f, glow)
            glow.maskFilter = null

            paint.textSize = base
            for (d in 8 downTo 1) {
                paint.color = Color.rgb(28 + d * 5, 28 + d * 5, 28 + d * 5)
                paint.alpha = (alpha * 0.55f).toInt()
                canvas.drawText(word.text, x - d * 0.9f, y + d * 0.9f, paint)
            }

            paint.color = if (i == current) Color.WHITE else Color.rgb(190, 190, 190)
            paint.alpha = alpha
            canvas.drawText(word.text, x, y, paint)
            canvas.restore()
        }

        paint.textSize = 12f
        paint.color = Color.rgb(110, 110, 110)
        paint.alpha = 255
        canvas.drawText(
            "BUMP  •  ${formatTime(timelineMs)}  •  ${if (running) "PLAYING" else "PAUSED"}",
            cx, height - 28f, paint
        )
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) < 24f) toggle()
                return true
            }
        }
        return true
    }
}
