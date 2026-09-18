package com.bump.visualizer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.HorizontalScrollView

class MainActivity : Activity() {
    private lateinit var visualizer: LyricVisualizerView
    private lateinit var audio: AudioPlayerController
    private val handler = Handler(Looper.getMainLooper())
    private var fisheyeOn = false
    private var visualModeIndex = 0
    private val songPickerRequest = 100
    private val lyricPickerRequest = 101
    private lateinit var timeline: SeekBar
    private lateinit var timelineLabel: TextView
    private lateinit var wordTimeline: WordTimelineView

    private val syncTask = object : Runnable {
        override fun run() {
            if (::audio.isInitialized) {
                visualizer.syncTo(audio.positionMs, audio.isPlaying)
                if (audio.durationMs > 0) wordTimeline.setDuration(audio.durationMs)
                wordTimeline.setCursor(audio.positionMs)
                if (audio.durationMs > 0) {
                    timeline.progress = ((audio.positionMs * 1000L) / audio.durationMs).toInt().coerceIn(0, 1000)
                    timelineLabel.text = "TIMELINE ${formatTime(audio.positionMs)} / ${formatTime(audio.durationMs)}"
                }
            }
            handler.postDelayed(this, 33L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        audio = AudioPlayerController(this)
        visualizer = LyricVisualizerView(this)

        val root = FrameLayout(this).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            addView(visualizer, FrameLayout.LayoutParams(-1, -1))
        }

        val title = TextView(this).apply {
            text = "BUMP"
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            alpha = 0.75f
            gravity = Gravity.CENTER
        }
        root.addView(title, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 22
        })

        val status = TextView(this).apply {
            text = "SOURCE: LOCAL AUDIO"
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            alpha = 0.65f
            gravity = Gravity.CENTER
        }

        fun editButton(label: String, action: () -> Unit): Button =
            Button(this).apply {
                text = label
                setOnClickListener { action() }
            }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        controls.addView(editButton("OPEN SONG") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "audio/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, songPickerRequest)
        })
        controls.addView(editButton("OPEN LRC") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "text/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }, lyricPickerRequest)
        })
        controls.addView(editButton("PLAY / PAUSE") { audio.toggle() })
        controls.addView(editButton("SPOTIFY") {
            val opened = StreamingAppLauncher.open(this, StreamingProvider.SPOTIFY)
            status.text = if (opened) "SOURCE: SPOTIFY • CONTROL ONLY" else "SOURCE: LOCAL AUDIO • SPOTIFY NOT INSTALLED"
        })
        controls.addView(editButton("APPLE MUSIC") {
            val opened = StreamingAppLauncher.open(this, StreamingProvider.APPLE_MUSIC)
            status.text = if (opened) "SOURCE: APPLE MUSIC • SDK SETUP REQUIRED" else "SOURCE: LOCAL AUDIO • APPLE MUSIC NOT INSTALLED"
        })

        lateinit var modeButton: Button
        modeButton = editButton("SHIP") {
            visualModeIndex = (visualModeIndex + 1) % 4
            val next = when (visualModeIndex) {
                0 -> VisualMode.SHIP
                1 -> VisualMode.STACK
                2 -> VisualMode.TUNNEL
                else -> VisualMode.GLITCH
            }
            modeButton.text = next.name
            visualizer.setVisualMode(next)
        }
        controls.addView(modeButton)

        controls.addView(editButton("FISHEYE") {
            fisheyeOn = !fisheyeOn
            if (fisheyeOn) visualizer.post { FisheyeEffect.apply(visualizer, 0.18f) }
            else FisheyeEffect.clear(visualizer)
        })

        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        timeline = SeekBar(this).apply { max = 1000 }
        timelineLabel = TextView(this).apply {
            text = "TIMELINE 00:00 / 00:00"
            textSize = 11f
            setTextColor(android.graphics.Color.WHITE)
            gravity = Gravity.CENTER
        }
        timeline.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar, p: Int, fromUser: Boolean) {
                if (fromUser && audio.durationMs > 0) audio.seekTo(audio.durationMs * p / 1000L)
            }
            override fun onStartTrackingTouch(s: SeekBar) {}
            override fun onStopTrackingTouch(s: SeekBar) {}
        })
        editor.addView(timeline)
        editor.addView(timelineLabel)

        wordTimeline = WordTimelineView(this).apply {
            setDuration(if (audio.durationMs > 0) audio.durationMs else 60000L)
            setWords(visualizer.exportWords())
            setSelected(visualizer.selectedIndex())
            onWordSelected = { index ->
                visualizer.selectWord(index)
                setSelected(index)
            }
            onWordChanged = { index, start, end ->
                visualizer.setWordTiming(index, start, end)
                setWords(visualizer.exportWords())
                setSelected(index)
            }
            onSeek = { positionMs ->
                if (audio.durationMs > 0) audio.seekTo(positionMs)
            }
        }
        val timelineScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = true
            addView(wordTimeline, HorizontalScrollView.LayoutParams(wordTimeline.contentWidth(), 170))
        }
        editor.addView(timelineScroll)

        val zoomControls = LinearLayout(this).apply { gravity = Gravity.CENTER }
        zoomControls.addView(editButton("ZOOM -") {
            wordTimeline.zoomOut()
            wordTimeline.requestLayout()
        })
        zoomControls.addView(editButton("ZOOM +") {
            wordTimeline.zoomIn()
            wordTimeline.requestLayout()
        })
        zoomControls.addView(editButton("CENTER") {
            val target = visualizer.selectedWord()?.startMs ?: 0L
            wordTimeline.setCursor(target)
            if (audio.durationMs > 0) audio.seekTo(target)
        })
        editor.addView(zoomControls)

        val navigation = LinearLayout(this).apply { gravity = Gravity.CENTER }
        navigation.addView(editButton("PREV") {
            if (visualizer.wordCount() > 0) visualizer.selectWord((visualizer.selectedIndex() - 1).coerceAtLeast(0))
        })
        navigation.addView(editButton("NEXT") {
            if (visualizer.wordCount() > 0) visualizer.selectWord((visualizer.selectedIndex() + 1).coerceAtMost(visualizer.wordCount() - 1))
        })
        navigation.addView(editButton("START -") { visualizer.adjustSelected(dStartMs = -40L) })
        navigation.addView(editButton("START +") { visualizer.adjustSelected(dStartMs = 40L) })
        navigation.addView(editButton("END -") { visualizer.adjustSelected(dEndMs = -40L) })
        navigation.addView(editButton("END +") { visualizer.adjustSelected(dEndMs = 40L) })
        navigation.addView(editButton("UNDO") { visualizer.undo() })
        navigation.addView(editButton("REDO") { visualizer.redo() })
        editor.addView(navigation)

        val transform = LinearLayout(this).apply { gravity = Gravity.CENTER }
        transform.addView(editButton("X-") { visualizer.adjustSelected(dx = -12f) })
        transform.addView(editButton("X+") { visualizer.adjustSelected(dx = 12f) })
        transform.addView(editButton("Y-") { visualizer.adjustSelected(dy = -12f) })
        transform.addView(editButton("Y+") { visualizer.adjustSelected(dy = 12f) })
        transform.addView(editButton("Z-") { visualizer.adjustSelected(dz = -30f) })
        transform.addView(editButton("Z+") { visualizer.adjustSelected(dz = 30f) })
        transform.addView(editButton("RX") { visualizer.adjustSelected(dRotX = 5f) })
        transform.addView(editButton("RY") { visualizer.adjustSelected(dRotY = 5f) })
        transform.addView(editButton("S+") { visualizer.adjustSelected(dScale = 0.05f) })
        transform.addView(editButton("S-") { visualizer.adjustSelected(dScale = -0.05f) })
        editor.addView(transform)

        val projectControls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        projectControls.addView(editButton("SAVE PROJECT") {
            ProjectStore.save(this@MainActivity, visualizer.exportWords(), visualizer.visualMode())
            status.text = "PROJECT SAVED"
        })
        projectControls.addView(editButton("LOAD PROJECT") {
            ProjectStore.load(this@MainActivity)?.let { project ->
                visualizer.setWords(project.words)
                wordTimeline.setWords(visualizer.exportWords())
                visualizer.setVisualMode(project.mode)
                visualModeIndex = when (project.mode) {
                    VisualMode.SHIP -> 0
                    VisualMode.STACK -> 1
                    VisualMode.TUNNEL -> 2
                    VisualMode.GLITCH -> 3
                }
                modeButton.text = project.mode.name
                status.text = "PROJECT LOADED"
            } ?: run {
                status.text = "NO SAVED PROJECT"
            }
        })

        root.addView(status, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 150
        })
        root.addView(editor, FrameLayout.LayoutParams(-1, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 54
        })
        root.addView(controls, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 4
        })
        root.addView(projectControls, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 92
        })

        setContentView(root)
        handler.post(syncTask)
    }

    @Deprecated("Use Activity Result APIs in a later UI pass")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        data?.data?.let { uri ->
            if (requestCode == lyricPickerRequest) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                LyricFileLoader.load(this, uri)?.let { text ->
                    visualizer.setWords(LrcParser.parse(text))
                    wordTimeline.setWords(visualizer.exportWords())
                    wordTimeline.setSelected(visualizer.selectedIndex())
                }
            } else if (requestCode == songPickerRequest) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                audio.open(uri)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val s = ms.coerceAtLeast(0L) / 1000L
        return "%02d:%02d".format(s / 60L, s % 60L)
    }

    override fun onDestroy() {
        handler.removeCallbacks(syncTask)
        audio.release()
        super.onDestroy()
    }
}
