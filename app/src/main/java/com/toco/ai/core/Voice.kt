package com.toco.ai.core

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Single shared TTS engine. Held app-wide so it isn't re-initialised on every
 * spoken line (which is what causes the classic "first word gets cut" bug).
 *
 * TOCO speaks on STREAM_RING.
 *
 * Why not the call stream: that required forcing the device into
 * MODE_IN_COMMUNICATION and switching speakerphone on, because the voice-call
 * stream otherwise routes to the earpiece. That mode is global — every other
 * app sees it — and if it ever failed to restore, the volume keys stayed stuck
 * on call volume. Too much blast radius for a volume preference.
 *
 * The ring stream needs none of that. It already plays out of the loudspeaker
 * at ringtone loudness, and the volume keys show "Ring" while TOCO talks.
 *
 * One honest consequence: with the phone on silent, ring volume is zero and
 * TOCO will be inaudible. That is the same rule your ringtone follows, and the
 * settings screen reports it rather than leaving you guessing.
 */
object Voice {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = mutableListOf<String>()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
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

    /** The live engine, for callers that need to set pitch, rate or voice. */
    fun engine(): TextToSpeech? = if (ready) tts else null

    fun speak(context: Context, text: String) {
        init(context)
        if (text.isBlank()) return
        if (ready) say(text) else queue += text
    }

    private fun say(text: String) {
        val context = appContext ?: return

        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, stream(context))
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, UTTERANCE_ID)
    }

    /** Ring stream by default; media only if the user turned the setting off. */
    private fun stream(context: Context): Int =
        if (Prefs(context).loudVoice) AudioManager.STREAM_RING
        else AudioManager.STREAM_MUSIC

    /**
     * Current ring volume as a percentage. Zero means the phone is silenced
     * and nothing TOCO says will be heard.
     */
    fun ringVolumePercent(context: Context): Int {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return -1
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_RING)
        if (max <= 0) return -1
        return audio.getStreamVolume(AudioManager.STREAM_RING) * 100 / max
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }

    private const val UTTERANCE_ID = "toco"
}
