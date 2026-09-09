package com.toco.ai.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Single shared TTS engine. Held app-wide so it isn't re-initialised on every
 * spoken line (which is what causes the classic "first word gets cut" bug).
 */
object Voice {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = mutableListOf<String>()

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.US
                queue.forEach { say(it) }
                queue.clear()
            }
        }
    }

    fun speak(context: Context, text: String) {
        init(context)
        if (ready) say(text) else queue += text
    }

    private fun say(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, "toco")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
