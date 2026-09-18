package com.bump.visualizer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var visualizer: LyricVisualizerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        val pick = Button(this).apply {
            text = "OPEN SONG"
            setOnClickListener {
                startActivityForResult(
                    Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        type = "audio/*"
                        addCategory(Intent.CATEGORY_OPENABLE)
                    }, 100
                )
            }
        }
        root.addView(pick, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 4
        })

        setContentView(root)
    }
}
