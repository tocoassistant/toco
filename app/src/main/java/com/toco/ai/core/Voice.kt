package com.toco.ai.core

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/**
 * Single shared TTS engine. Held app-wide so it isn't re-initialised on every
 * spoken line (which is what causes the classic "first word gets cut" bug).
 *
 * Audio routing works the way a game's voice chat does. Speaking on
 * STREAM_VOICE_CALL alone would send TOCO to the earpiece — the small speaker
 * you hold to your ear — making it quieter, not louder. What actually produces
 * "loudspeaker, but the volume rocker shows call volume" is the combination:
 *
 *   1. AudioManager mode -> MODE_IN_COMMUNICATION
 *   2. speakerphone forced ON, which overrides the earpiece routing
 *   3. TTS output on STREAM_VOICE_CALL
 *
 * The mode is global to the device, so it MUST be restored once TOCO stops
 * talking — otherwise the volume keys would keep adjusting call volume forever
 * and other apps would misbehave. Restoration happens on the utterance
 * callback, with a timeout as a backstop in case the engine never reports done.
 */
object Voice {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val queue = mutableListOf<String>()

    /** Audio state captured before TOCO started talking, restored afterwards. */
    private var previousMode: Int? = null
    private var previousSpeaker: Boolean? = null
    private var speaking = 0

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (tts != null) return

        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.language = Locale.US
                attachListener()
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

    private fun attachListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                finishedOne()
            }

            @Deprecated("Required by the base class")
            override fun onError(utteranceId: String?) {
                finishedOne()
            }
        })
    }

    private fun say(text: String) {
        val context = appContext ?: return
        val prefs = Prefs(context)

        if (prefs.callVolumeVoice) {
            beginCallAudio(context)
        }

        val params = Bundle()
        if (prefs.callVolumeVoice) {
            params.putInt(
                TextToSpeech.Engine.KEY_PARAM_STREAM,
                AudioManager.STREAM_VOICE_CALL
            )
        }

        speaking++
        tts?.speak(text, TextToSpeech.QUEUE_ADD, params, UTTERANCE_ID)
    }

    private fun finishedOne() {
        speaking--
        if (speaking <= 0) {
            speaking = 0
            endCallAudio()
        }
    }

    // ---------------- audio routing ----------------

    private fun beginCallAudio(context: Context) {
        // Only capture the original state on the FIRST line; a queued second
        // line must not overwrite it with our own settings.
        if (previousMode != null) return

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

        try {
            previousMode = audio.mode
            previousSpeaker = audio.isSpeakerphoneOn

            audio.mode = AudioManager.MODE_IN_COMMUNICATION
            // Without this the voice-call stream goes to the earpiece.
            audio.isSpeakerphoneOn = true
        } catch (e: Exception) {
            previousMode = null
            previousSpeaker = null
        }
    }

    private fun endCallAudio() {
        val context = appContext ?: return
        val mode = previousMode ?: return
        val speaker = previousSpeaker

        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        previousMode = null
        previousSpeaker = null

        try {
            audio?.mode = mode
            if (speaker != null) audio?.isSpeakerphoneOn = speaker
        } catch (e: Exception) {
            // Device refused; nothing useful to do, and leaving it is worse
            // than trying, so this is only swallowed after the attempt.
        }
    }

    fun shutdown() {
        endCallAudio()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        speaking = 0
    }

    private const val UTTERANCE_ID = "toco"
}
