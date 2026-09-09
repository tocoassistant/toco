package com.toco.ai.ui.debug

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

/**
 * Plain, dependency-free crash screen. Built in code rather than XML so it
 * still works even if resource loading is what broke.
 */
class CrashActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val trace = intent.getStringExtra(EXTRA_TRACE) ?: "Unknown crash"

        val text = TextView(this).apply {
            setText(trace)
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(32, 64, 32, 64)
            setTextIsSelectable(true)
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(text)
            }
        )
    }

    companion object {
        const val EXTRA_TRACE = "toco.extra.TRACE"
    }
}
