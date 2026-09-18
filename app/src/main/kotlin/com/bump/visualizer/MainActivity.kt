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
        controls.addView(fisheye)

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
