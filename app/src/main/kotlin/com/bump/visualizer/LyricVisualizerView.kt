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
    private var selectedKeyframeIndex = -1
    private var motionSettings = MotionSettings()
    private var maskMode = MaskMode.NONE
    private var selectedKeyframeIndex = -1
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

    private fun cloneWord(w: LyricWord) = w.copy(keyframes = w.keyframes.map { it.copy() }.toMutableList())

    fun setWords(newWords: List<LyricWord>) {
        words = newWords.map { cloneWord(it) }.toMutableList()
        selectedIndex = -1
        selectedKeyframeIndex = -1
        timelineMs = 0L
        undoStack.clear()
        redoStack.clear()
        invalidate()
    }

    fun exportWords(): List<LyricWord> = words.map { cloneWord(it) }

    fun selectedWord(): LyricWord? = words.getOrNull(selectedIndex)
    fun selectedIndex(): Int = selectedIndex
    fun selectedKeyframeIndex(): Int = selectedKeyframeIndex
    fun selectedKeyframe(): LyricKeyframe? = selectedWord()?.keyframes?.getOrNull(selectedKeyframeIndex)

    fun selectWord(index: Int) {
        if (words.isEmpty()) {
            selectedIndex = -1
            selectedKeyframeIndex = -1
            return
        }
        selectedIndex = index.coerceIn(0, words.lastIndex)
        selectedKeyframeIndex = -1
        invalidate()
    }

    fun selectKeyframe(wordIndex: Int, keyframeIndex: Int) {
        val w = words.getOrNull(wordIndex) ?: return
        if (keyframeIndex !in w.keyframes.indices) return
        selectedIndex = wordIndex
        selectedKeyframeIndex = keyframeIndex
        timelineMs = w.keyframes[keyframeIndex].timeMs
        invalidate()
    }

    fun wordCount(): Int = words.size

    fun selectKeyframe(index: Int) {
        val w = selectedWord() ?: return
        selectedKeyframeIndex = if (index in w.keyframes.indices) index else -1
        invalidate()
    }

    fun keyframeCount(): Int = selectedWord()?.keyframes?.size ?: 0

    fun moveSelectedKeyframe(newTimeMs: Long) {
        val w = selectedWord() ?: return
        val k = w.keyframes.getOrNull(selectedKeyframeIndex) ?: return
        val others = w.keyframes.filterIndexed { i, _ -> i != selectedKeyframeIndex }
        val minTime = others.filter { it.timeMs < k.timeMs }.maxOfOrNull { it.timeMs + 20L } ?: 0L
        val maxTime = others.filter { it.timeMs > k.timeMs }.minOfOrNull { it.timeMs - 20L } ?: Long.MAX_VALUE
        val next = newTimeMs.coerceIn(minTime, maxTime)
        if (next == k.timeMs) return
        pushUndo()
        w.keyframes[selectedKeyframeIndex] = k.copy(timeMs = next)
        w.keyframes.sortBy { it.timeMs }
        selectedKeyframeIndex = w.keyframes.indexOfFirst { it.timeMs == next }
        invalidate()
    }

    fun deleteSelectedKeyframe() {
        val w = selectedWord() ?: return
        if (selectedKeyframeIndex !in w.keyframes.indices) return
        pushUndo()
        w.keyframes.removeAt(selectedKeyframeIndex)
        selectedKeyframeIndex = -1
        invalidate()
    }

    fun adjustSelectedKeyframe(dx: Float = 0f, dy: Float = 0f, dz: Float = 0f,
                               dRotX: Float = 0f, dRotY: Float = 0f,
                               dScale: Float = 0f, dOpacity: Float = 0f) {
        val w = selectedWord() ?: return
        val k = w.keyframes.getOrNull(selectedKeyframeIndex) ?: return
        pushUndo()
        w.keyframes[selectedKeyframeIndex] = k.copy(
            x = k.x + dx, y = k.y + dy, z = k.z + dz,
            rotationX = k.rotationX + dRotX, rotationY = k.rotationY + dRotY,
            scale = (k.scale + dScale).coerceIn(0.05f, 5f),
            opacity = (k.opacity + dOpacity).coerceIn(0f, 1f)
        )
        invalidate()
    }

    fun setMotionSettings(settings: MotionSettings) { motionSettings = settings; invalidate() }
    fun motionSettings(): MotionSettings = motionSettings
    fun setMaskMode(mode: MaskMode) { maskMode = mode; invalidate() }
    fun maskMode(): MaskMode = maskMode

    fun setWordTiming(index: Int, startMs: Long, endMs: Long) {
        val w = words.getOrNull(index) ?: return
        val ns = startMs.coerceAtLeast(0L)
        val ne = endMs.coerceAtLeast(ns + 80L)
        if (w.startMs == ns && w.endMs == ne) return
        w.startMs = ns
        w.endMs = ne
        invalidate()
    }

    fun beginTimingEdit() { if (timingEditSnapshot == null) timingEditSnapshot = snapshot() }

    fun endTimingEdit() {
        val before = timingEditSnapshot ?: return
        timingEditSnapshot = null
        if (before.words != words) {
            undoStack.addLast(before)
            if (undoStack.size > 40) undoStack.removeFirst()
            redoStack.clear()
        }
    }

    fun beginKeyframeEdit() { if (keyframeEditSnapshot == null) keyframeEditSnapshot = snapshot() }

    fun endKeyframeEdit() {
        val before = keyframeEditSnapshot ?: return
        keyframeEditSnapshot = null
        if (before.words != words) {
            undoStack.addLast(before)
            if (undoStack.size > 40) undoStack.removeFirst()
            redoStack.clear()
        }
    }

    fun addKeyframe(timeMs: Long = timelineMs): Int {
        val w = selectedWord() ?: return -1
        pushUndo()
        val state = KeyframeEngine.evaluate(w, timeMs)
        val t = timeMs.coerceAtLeast(0L)
        w.keyframes.removeAll { abs(it.timeMs - t) < 20L }
        w.keyframes.add(LyricKeyframe(t, state.x, state.y, state.z, state.rotationX, state.rotationY, state.scale, state.opacity))
        w.keyframes.sortBy { it.timeMs }
        selectedKeyframeIndex = w.keyframes.indexOfFirst { it.timeMs == t }
        timelineMs = t
        invalidate()
        return selectedKeyframeIndex
    }

    fun removeSelectedKeyframe() {
        val w = selectedWord() ?: return
        if (selectedKeyframeIndex !in w.keyframes.indices) return
        pushUndo()
        w.keyframes.removeAt(selectedKeyframeIndex)
        selectedKeyframeIndex = (selectedKeyframeIndex - 1).coerceAtLeast(0).takeIf { w.keyframes.isNotEmpty() } ?: -1
        invalidate()
    }

    fun removeNearestKeyframe(timeMs: Long = timelineMs) {
        val w = selectedWord() ?: return
        val index = w.keyframes.indices.minByOrNull { abs(w.keyframes[it].timeMs - timeMs) } ?: return
        if (abs(w.keyframes[index].timeMs - timeMs) > 250L) return
        selectKeyframe(selectedIndex, index)
        removeSelectedKeyframe()
    }

    fun keyframeCount(): Int = selectedWord()?.keyframes?.size ?: 0

    fun setSelectedKeyframeValues(timeMs: Long, x: Float, y: Float, z: Float, rotationX: Float, rotationY: Float, scale: Float, opacity: Float) {
        val w = selectedWord() ?: return
        if (selectedKeyframeIndex !in w.keyframes.indices) return
        val old = w.keyframes[selectedKeyframeIndex]
        val updated = old.copy(
            timeMs = timeMs.coerceAtLeast(0L),
            x = x, y = y, z = z,
            rotationX = rotationX, rotationY = rotationY,
            scale = scale.coerceIn(0.05f, 8f),
            opacity = opacity.coerceIn(0f, 1f)
        )
        pushUndo()
        w.keyframes[selectedKeyframeIndex] = updated
        w.keyframes.sortBy { it.timeMs }
        selectedKeyframeIndex = w.keyframes.indexOfFirst { it.timeMs == updated.timeMs && it.x == updated.x && it.y == updated.y && it.z == updated.z }
        timelineMs = updated.timeMs
        invalidate()
    }

    fun moveSelectedKeyframeTime(timeMs: Long) {
        val w = selectedWord() ?: return
        if (selectedKeyframeIndex !in w.keyframes.indices) return
        val old = w.keyframes[selectedKeyframeIndex]
        val previous = if (selectedKeyframeIndex > 0) w.keyframes[selectedKeyframeIndex - 1].timeMs else 0L
        val next = if (selectedKeyframeIndex < w.keyframes.lastIndex) w.keyframes[selectedKeyframeIndex + 1].timeMs else Long.MAX_VALUE
        val t = timeMs.coerceIn(previous + 1L, (next - 1L).coerceAtLeast(previous + 1L))
        if (t == old.timeMs) return
        w.keyframes[selectedKeyframeIndex] = old.copy(timeMs = t)
        timelineMs = t
        invalidate()
    }

    fun setSelectedKeyframeTime(timeMs: Long) {
        val k = selectedKeyframe() ?: return
        setSelectedKeyframeValues(timeMs, k.x, k.y, k.z, k.rotationX, k.rotationY, k.scale, k.opacity)
    }

    fun setVisualMode(mode: VisualMode) { visualMode = mode; invalidate() }
    fun visualMode(): VisualMode = visualMode

    private data class EditSnapshot(val words: List<LyricWord>, val selected: Int, val selectedKeyframe: Int, val mode: VisualMode)
    private val undoStack = ArrayDeque<EditSnapshot>()
    private val redoStack = ArrayDeque<EditSnapshot>()
    private var timingEditSnapshot: EditSnapshot? = null
    private var keyframeEditSnapshot: EditSnapshot? = null

    private fun snapshot() = EditSnapshot(words.map { cloneWord(it) }, selectedIndex, selectedKeyframeIndex, visualMode)
    private fun pushUndo() {
        undoStack.addLast(snapshot())
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
    }

    fun undo() {
        val s = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(snapshot())
        words = s.words.map { cloneWord(it) }
        selectedIndex = s.selected
        selectedKeyframeIndex = s.selectedKeyframe
        visualMode = s.mode
        invalidate()
    }

    fun redo() {
        val s = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(snapshot())
        words = s.words.map { cloneWord(it) }
        selectedIndex = s.selected
        selectedKeyframeIndex = s.selectedKeyframe
        visualMode = s.mode
        invalidate()
    }

    fun adjustSelected(dx: Float = 0f, dy: Float = 0f, dz: Float = 0f, dRotX: Float = 0f, dRotY: Float = 0f, dScale: Float = 0f, dStartMs: Long = 0L, dEndMs: Long = 0L) {
        val w = selectedWord() ?: return
        val k = selectedKeyframe()
        if (k != null) {
            pushUndo()
            w.keyframes[selectedKeyframeIndex] = k.copy(
                x = k.x + dx,
                y = k.y + dy,
                z = k.z + dz,
                rotationX = k.rotationX + dRotX,
                rotationY = k.rotationY + dRotY,
                scale = (k.scale + dScale).coerceIn(0.05f, 8f)
            )
            timelineMs = w.keyframes[selectedKeyframeIndex].timeMs
            invalidate()
            return
        }

        pushUndo()
        w.x += dx; w.y += dy; w.z += dz
        w.rotationX += dRotX; w.rotationY += dRotY
        w.scale = (w.scale + dScale).coerceIn(0.25f, 3f)
        w.startMs = (w.startMs + dStartMs).coerceAtLeast(0L)
        w.endMs = (w.endMs + dEndMs).coerceAtLeast(w.startMs + 80L)
        invalidate()
    }

    fun syncTo(ms: Long, playing: Boolean) {
        externalClock = true
        timelineMs = ms.coerceAtLeast(0L)
        running = playing
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
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

        val first = (current - 12).coerceAtLeast(0)
        val last = (current + 10).coerceAtMost(words.lastIndex)
        val ordered = (first..last).toList().sortedByDescending { it }

        for (i in ordered) {
            val word = words[i]
            val animated = KeyframeEngine.evaluate(word, timelineMs)
            val isSelected = i == selectedIndex
            val depthIndex = i - current
            val age = timelineMs - word.startMs
            val progress = (age / 900f).coerceIn(-1f, 1.5f)
            val depthStep = when (visualMode) {
                VisualMode.SHIP -> 210f
                VisualMode.STACK -> 155f
                VisualMode.TUNNEL -> 330f
                VisualMode.GLITCH -> 250f
            }
            val z = animated.z + MotionMath.curvedDepth(depthIndex, depthStep, motionSettings.depthCurve) - max(0f, progress) * 85f
            val perspective = 1f / (1f + abs(z) / motionSettings.perspective)
            val drift = sin(modePhase * 0.65f + i * 1.17f)
            val sway = cos(modePhase * 0.42f + i * 0.73f)
            val glitch = if (visualMode == VisualMode.GLITCH && i == current) sin(modePhase * 22f) * 24f else 0f
            val x = cx + animated.x + glitch + (sin(i * 1.91f) * (120f + abs(depthIndex) * 28f) + sway * 24f) * perspective
            val y = cy + (animated.y + (depthIndex * 108f + drift * 30f)) * perspective
            val rotationY = animated.rotationY + sin(i * 0.61f + modePhase * 0.32f) * 30f + depthIndex * 3.5f
            val rotationX = animated.rotationX + cos(i * 0.47f + modePhase * 0.25f) * 12f
            val isCurrent = i == current
            val scale = perspective * (if (isCurrent) 1.18f else 0.86f) * animated.scale
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

            for (d in 12 downTo 1) {
                edge.textSize = size
                val shade = 20 + d * 3
                edge.color = Color.rgb(shade, shade, shade)
                edge.alpha = (alpha * animated.opacity * (0.16f + d / 80f)).toInt().coerceAtMost(180)
                canvas.drawText(word.text, x - d * 1.35f, y + d * 1.35f, edge)
            }

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
            face.alpha = (alpha * animated.opacity).toInt().coerceIn(0, 255)
            canvas.drawText(word.text, x, y, face)

            if (isSelected) {
                edge.style = Paint.Style.STROKE
                edge.strokeWidth = 2f
                edge.color = Color.WHITE
                edge.alpha = 180
                val bw = max(70f, face.measureText(word.text) + 26f)
                canvas.drawRect(x - bw / 2f, y - size, x + bw / 2f, y + 16f, edge)
                edge.style = Paint.Style.FILL
            }
            canvas.restore()
        }

        face.textSize = 12f
        face.color = Color.rgb(105, 105, 105)
        face.alpha = 255
        canvas.drawText(
            "BUMP  •  ${visualMode.name}  •  ${formatTime(timelineMs)}  •  ${if (running) "PLAYING" else "PAUSED"}",
            cx, height - 28f, face
        )
    }

    private fun applyMask(canvas: Canvas, mode: MaskMode) {
        when (mode) {
            MaskMode.NONE -> Unit
            MaskMode.CINEMA -> {
                val bar = height * 0.095f
                canvas.clipRect(0f, bar, width.toFloat(), height - bar)
            }
            MaskMode.CIRCLE -> {
                val r = min(width, height) * 0.47f
                canvas.clipPath(Path().apply { addCircle(width / 2f, height / 2f, r, Path.Direction.CW) })
            }
            MaskMode.ROUNDED -> {
                val ix = width * 0.035f
                val iy = height * 0.06f
                canvas.clipPath(Path().apply {
                    addRoundRect(ix, iy, width - ix, height - iy, 48f, 48f, Path.Direction.CW)
                })
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val s = ms / 1000
        return "%02d:%02d".format(s / 60, s % 60)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                dragX = event.x
                dragY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                selectedWord()?.let { w ->
                    if (selectedKeyframe() == null) {
                        w.x += event.x - dragX
                        w.y += event.y - dragY
                    }
                }
                dragX = event.x
                dragY = event.y
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) < 24f && words.isNotEmpty()) {
                    selectedIndex = words.indexOfLast { timelineMs >= it.startMs }.coerceIn(0, words.lastIndex)
                    selectedKeyframeIndex = -1
                    lastNanos = System.nanoTime()
                    invalidate()
                }
                return true
            }
        }
        return true
    }
}
