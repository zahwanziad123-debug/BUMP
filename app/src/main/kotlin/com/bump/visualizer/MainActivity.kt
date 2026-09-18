package com.bump.visualizer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var visualizer: LyricVisualizerView
    private lateinit var audio: AudioPlayerController
    private val handler = Handler(Looper.getMainLooper())
    private var fisheyeOn = false
    private val songPickerRequest = 100
    private val lyricPickerRequest = 101

    private val syncTask = object : Runnable {
        override fun run() {
            if (::audio.isInitialized) {
                visualizer.syncTo(audio.positionMs, audio.isPlaying)
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

        val providerStatus = TextView(this).apply {
            text = "LOCAL AUDIO"
            textSize = 12f
            setTextColor(android.graphics.Color.WHITE)
            alpha = 0.65f
            gravity = Gravity.CENTER
        }

        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }

        val pick = Button(this).apply {
            text = "OPEN SONG"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "audio/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    },
                    songPickerRequest
                )
            }
        }

        val pickLrc = Button(this).apply {
            text = "OPEN LRC"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "text/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    },
                    lyricPickerRequest
                )
            }
        }

        val play = Button(this).apply {
            text = "PLAY / PAUSE"
            setOnClickListener { audio.toggle() }
        }

        val spotify = Button(this).apply {
            text = "SPOTIFY"
            setOnClickListener {
                val opened = StreamingAppLauncher.open(this@MainActivity, StreamingProvider.SPOTIFY)
                providerStatus.text = if (opened) "SPOTIFY OPENED — BUMP STREAMING CONNECTION" else "SPOTIFY NOT INSTALLED"
            }
        }

        val appleMusic = Button(this).apply {
            text = "APPLE MUSIC"
            setOnClickListener {
                val opened = StreamingAppLauncher.open(this@MainActivity, StreamingProvider.APPLE_MUSIC)
                providerStatus.text = if (opened) "APPLE MUSIC OPENED — BUMP STREAMING CONNECTION" else "APPLE MUSIC NOT INSTALLED"
            }
        }

        val fisheye = Button(this).apply {
            text = "FISHEYE"
            setOnClickListener {
                fisheyeOn = !fisheyeOn
                if (fisheyeOn) visualizer.post { FisheyeEffect.apply(visualizer, 0.18f) }
                else FisheyeEffect.clear(visualizer)
            }
        }

        controls.addView(pick)
        controls.addView(pickLrc)
        controls.addView(play)
        controls.addView(spotify)
        controls.addView(appleMusic)
        controls.addView(fisheye)

        val editor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        fun editButton(label: String, action: () -> Unit): Button =
            Button(this).apply { text = label; setOnClickListener { action() } }

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

        root.addView(providerStatus, FrameLayout.LayoutParams(-1, -2).apply {
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
                }
            } else if (requestCode == songPickerRequest) {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                audio.open(uri)
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(syncTask)
        audio.release()
        super.onDestroy()
    }
}
