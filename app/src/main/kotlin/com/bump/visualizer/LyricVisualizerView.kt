package com.bump.visualizer

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class LyricVisualizerView(context: Context) : View(context), Choreographer.FrameCallback {
    private val words = LyricTimeline.demo()
    private val face = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = face.typeface
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
    }
    private val camera = Camera()
    private val matrix = Matrix()
    private var timelineMs = 0L
    private var running = true
    private var lastNanos = System.nanoTime()
    private var downX = 0f
    private var modePhase = 0f

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        camera.setLocation(0f, 0f, -8f)
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        val delta = ((frameTimeNanos - lastNanos) / 1_000_000L).coerceIn(0L, 50L)
        if (running) {
            timelineMs += delta
            modePhase += delta / 1000f
        }
        lastNanos = frameTimeNanos
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun syncTo(ms: Long, playing: Boolean) {\n        timelineMs = ms.coerceAtLeast(0L)\n        running = playing\n        invalidate()\n    }\n\n    fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (width == 0 || height == 0) return

        val cx = width / 2f
        val cy = height / 2f
        val current = words.indexOfLast { timelineMs >= it.startMs }.coerceIn(0, words.lastIndex)

        // Ship-style wall: the current word is closest to the viewer,
        // surrounding words form a drifting 3D field behind it.
        val first = (current - 12).coerceAtLeast(0)
        val last = (current + 10).coerceAtMost(words.lastIndex)
        val ordered = (first..last).toList().sortedByDescending { it }

        for (i in ordered) {
            val word = words[i]
            val depthIndex = i - current
            val age = timelineMs - word.startMs
            val progress = ((age / 900f).coerceIn(-1f, 1.5f))
            val z = depthIndex * 210f - max(0f, progress) * 85f

            // Perspective projection. Farther words shrink and move toward
            // a vanishing point near the center.
            val perspective = 1f / (1f + abs(z) / 1050f)
            val drift = sin(modePhase * 0.65f + i * 1.17f)
            val sway = cos(modePhase * 0.42f + i * 0.73f)

            val x = cx + (sin(i * 1.91f) * (120f + abs(depthIndex) * 28f) + sway * 24f) * perspective
            val y = cy + (depthIndex * 108f + drift * 30f) * perspective
            val rotationY = (sin(i * 0.61f + modePhase * 0.32f) * 30f) + depthIndex * 3.5f
            val rotationX = cos(i * 0.47f + modePhase * 0.25f) * 12f

            val isCurrent = i == current
            val scale = perspective * if (isCurrent) 1.18f else 0.86f
            val size = if (isCurrent) 82f else 48f * (0.92f + perspective * 0.08f)
            val alpha = if (isCurrent) 255 else (225f * perspective).toInt().coerceIn(25, 210)

            canvas.save()

            camera.save()
            camera.rotateX(rotationX)
            camera.rotateY(rotationY)
            camera.getMatrix(matrix)
            camera.restore()

            matrix.preTranslate(-cx, -cy)
            matrix.postTranslate(x, y)
            canvas.concat(matrix)
            canvas.scale(scale, scale, x, y)

            // Depth echoes: a stack of dim faces creates the heavy printed/
            // extruded appearance while staying cheap on the GPU-accelerated Canvas.
            for (d in 12 downTo 1) {
                edge.textSize = size
                val shade = 20 + d * 3
                edge.color = Color.rgb(shade, shade, shade)
                edge.alpha = (alpha * (0.16f + d / 80f)).toInt().coerceAtMost(180)
                canvas.drawText(word.text, x - d * 1.35f, y + d * 1.35f, edge)
            }

            // Motion ghosts behind the current word.
            if (isCurrent) {
                edge.textSize = size * 1.01f
                edge.color = Color.WHITE
                edge.alpha = 30
                canvas.drawText(word.text, x - 18f, y + 4f, edge)
                edge.alpha = 18
                canvas.drawText(word.text, x - 34f, y + 8f, edge)
            }

            face.textSize = size
            face.color = if (isCurrent) Color.WHITE else Color.rgb(178, 178, 178)
            face.alpha = alpha
            canvas.drawText(word.text, x, y, face)

            canvas.restore()
        }

        // Small cinematic HUD, intentionally unobtrusive.
        face.textSize = 12f
        face.color = Color.rgb(105, 105, 105)
        face.alpha = 255
        canvas.drawText(
            "BUMP  •  ${formatTime(timelineMs)}  •  ${if (running) "PLAYING" else "PAUSED"}",
            cx, height - 28f, face
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
                if (abs(event.x - downX) < 24f) {
                    running = !running
                    lastNanos = System.nanoTime()
                }
                return true
            }
        }
        return true
    }
}
