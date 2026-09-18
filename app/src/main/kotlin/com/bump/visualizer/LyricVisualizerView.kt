package com.bump.visualizer

import android.content.Context
import android.graphics.*
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

enum class VisualMode { SHIP, STACK, TUNNEL, GLITCH }

class LyricVisualizerView(context: Context) : View(context), Choreographer.FrameCallback {
    private var words: List<LyricWord> = LyricTimeline.demo()
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
    private var running = false
    private var externalClock = false
    private var lastNanos = System.nanoTime()
    private var downX = 0f
    private var modePhase = 0f
    private var visualMode = VisualMode.SHIP
    private var selectedIndex = -1
    private var dragX = 0f
    private var dragY = 0f

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        camera.setLocation(0f, 0f, -8f)
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun doFrame(frameTimeNanos: Long) {
        val delta = ((frameTimeNanos - lastNanos) / 1_000_000L).coerceIn(0L, 50L)
        if (!externalClock && running) timelineMs += delta
        modePhase += delta / 1000f
        lastNanos = frameTimeNanos
        invalidate()
        Choreographer.getInstance().postFrameCallback(this)
    }

    fun setWords(newWords: List<LyricWord>) { words = newWords.map { it.copy() }.toMutableList(); selectedIndex = -1; timelineMs = 0L; undoStack.clear(); redoStack.clear(); invalidate() }

    fun exportWords(): List<LyricWord> = words.map { it.copy() }

    fun selectedWord(): LyricWord? = words.getOrNull(selectedIndex)
    fun selectedIndex(): Int = selectedIndex

    fun selectWord(index: Int) {
        if (words.isEmpty()) { selectedIndex = -1; return }
        selectedIndex = index.coerceIn(0, words.lastIndex)
        invalidate()
    }
    fun wordCount(): Int = words.size

    fun setVisualMode(mode: VisualMode) { visualMode = mode; invalidate() }

    fun visualMode(): VisualMode = visualMode

    private data class EditSnapshot(val words: List<LyricWord>, val selected: Int, val mode: VisualMode)
    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private fun snapshot() = EditSnapshot(words.map { it.copy() }, selectedIndex, visualMode)
    private fun pushUndo() { undoStack.addLast(snapshot()); if (undoStack.size > 40) undoStack.removeFirst(); redoStack.clear() }
    fun undo() { val s = undoStack.removeLastOrNull() ?: return; redoStack.addLast(snapshot()); words = s.words.map { it.copy() }; selectedIndex = s.selected; visualMode = s.mode; invalidate() }
    fun redo() { val s = redoStack.removeLastOrNull() ?: return; undoStack.addLast(snapshot()); words = s.words.map { it.copy() }; selectedIndex = s.selected; visualMode = s.mode; invalidate() }

    fun adjustSelected(dx: Float = 0f, dy: Float = 0f, dz: Float = 0f, dRotX: Float = 0f, dRotY: Float = 0f, dScale: Float = 0f, dStartMs: Long = 0L, dEndMs: Long = 0L) {
        val w = selectedWord() ?: return
        pushUndo()
        w.x += dx; w.y += dy; w.z += dz; w.rotationX += dRotX; w.rotationY += dRotY
        w.scale = (w.scale + dScale).coerceIn(0.25f, 3f)
        w.startMs = (w.startMs + dStartMs).coerceAtLeast(0L)
        w.endMs = (w.endMs + dEndMs).coerceAtLeast(w.startMs + 80L)
        invalidate()
    }

    override fun syncTo(ms: Long, playing: Boolean) {
        externalClock = true
        timelineMs = ms.coerceAtLeast(0L)
        running = playing
        invalidate()
    }

    fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        if (width == 0 || height == 0) return
        if (words.isEmpty()) {
            face.textSize = 12f
            face.color = Color.rgb(105, 105, 105)
            face.alpha = 255
            canvas.drawText("BUMP  •  NO LYRICS", width / 2f, height / 2f, face)
            return
        }

        val cx = width / 2f
        val cy = height / 2f
        val current = words.indexOfLast { timelineMs >= it.startMs }.coerceIn(0, words.lastIndex)
        if (selectedIndex !in words.indices) selectedIndex = current

        // Ship-style wall: the current word is closest to the viewer,
        // surrounding words form a drifting 3D field behind it.
        val first = (current - 12).coerceAtLeast(0)
        val last = (current + 10).coerceAtMost(words.lastIndex)
        val ordered = (first..last).toList().sortedByDescending { it }

        for (i in ordered) {
            val word = words[i]
            val isSelected = i == selectedIndex
            val depthIndex = i - current
            val age = timelineMs - word.startMs
            val progress = ((age / 900f).coerceIn(-1f, 1.5f))
            val depthStep = when (visualMode) { VisualMode.SHIP -> 210f; VisualMode.STACK -> 155f; VisualMode.TUNNEL -> 330f; VisualMode.GLITCH -> 250f }
            val z = word.z + depthIndex * depthStep - max(0f, progress) * 85f

            // Perspective projection. Farther words shrink and move toward
            // a vanishing point near the center.
            val perspective = 1f / (1f + abs(z) / 1050f)
            val drift = sin(modePhase * 0.65f + i * 1.17f)
            val sway = cos(modePhase * 0.42f + i * 0.73f)

            val glitch = if (visualMode == VisualMode.GLITCH && i == current) sin(modePhase * 22f) * 24f else 0f
            val x = cx + word.x + glitch + (sin(i * 1.91f) * (120f + abs(depthIndex) * 28f) + sway * 24f) * perspective
            val y = cy + (word.y + (depthIndex * 108f + drift * 30f)) * perspective
            val rotationY = word.rotationY + (sin(i * 0.61f + modePhase * 0.32f) * 30f) + depthIndex * 3.5f
            val rotationX = word.rotationX + cos(i * 0.47f + modePhase * 0.25f) * 12f

            val isCurrent = i == current
            val scale = perspective * (if (isCurrent) 1.18f else 0.86f) * word.scale
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

            if (isSelected) {
                edge.style = Paint.Style.STROKE; edge.strokeWidth = 2f; edge.color = Color.WHITE; edge.alpha = 180
                val bw = max(70f, face.measureText(word.text) + 26f)
                canvas.drawRect(x - bw / 2f, y - size, x + bw / 2f, y + 16f, edge)
                edge.style = Paint.Style.FILL
            }
            canvas.restore()
        }

        // Small cinematic HUD, intentionally unobtrusive.
        face.textSize = 12f
        face.color = Color.rgb(105, 105, 105)
        face.alpha = 255
        canvas.drawText(
            "BUMP  •  ${visualMode.name}  •  ${formatTime(timelineMs)}  •  ${if (running) "PLAYING" else "PAUSED"}",
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
                downX = event.x; dragX = event.x; dragY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                selectedWord()?.let { w -> w.x += event.x - dragX; w.y += event.y - dragY }
                dragX = event.x; dragY = event.y; invalidate(); return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) < 24f && words.isNotEmpty()) {
                    selectedIndex = words.indexOfLast { timelineMs >= it.startMs }.coerceIn(0, words.lastIndex)
                    lastNanos = System.nanoTime()
                }
                return true
            }
        }
        return true
    }
}
