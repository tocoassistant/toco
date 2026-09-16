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

    /** Guards against a rebuild storm if the engine is genuinely unavailable. */
    private var lastRebuild = 0L

    fun init(context: Context) {
        appContext = context.applicationContext
        if (tts != null && ready) return

        // A half-built engine from a previous failed attempt must go, or the
        // old "tts != null so we are fine" assumption comes straight back.
        if (tts != null) release()

        build(context.applicationContext)
    }

    private fun build(app: Context) {
        lastRebuild = System.currentTimeMillis()

        tts = try {
            TextToSpeech(app) { status ->
                ready = status == TextToSpeech.SUCCESS

                if (ready) {
                    try {
                        tts?.language = Locale.US
                    } catch (e: Exception) {
                        // Locale unsupported; the engine default still speaks.
                    }
                    Voices.applySaved(app)

                    val pending = queue.toList()
                    queue.clear()
                    pending.forEach { say(it) }
                } else {
                    EventLog.log(app, "VOICE", "engine failed to connect (status=" + status + ")")
                    release()
                }
            }
        } catch (e: Exception) {
            ready = false
            null
        }
    }

    private fun release() {
        try {
            tts?.shutdown()
        } catch (e: Exception) {
            // Already gone.
        }
        tts = null
        ready = false
    }

    /**
     * The engine lives in its own process, which Android kills whenever it
     * needs the memory. When that happens the object here stays non-null but
     * every call to it silently does nothing — so TOCO would keep running
     * commands while saying nothing at all, which looks exactly like the whole
     * app has stopped working.
     *
     * speak() returns ERROR when the binding is dead, and that is the signal
     * to rebuild and try the line again.
     */
    private fun speakOrRebuild(text: String, mode: Int) {
        val app = appContext ?: return

        val engine = tts
        if (engine == null || !ready) {
            queue += text
            init(app)
            return
        }

        val params = Bundle()
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, stream(app))

        val result = try {
            engine.speak(text, mode, params, UTTERANCE_ID)
        } catch (e: Exception) {
            TextToSpeech.ERROR
        }

        if (result == TextToSpeech.ERROR) {
            EventLog.log(app, "VOICE", "speak FAILED, engine is dead - rebuilding")
            if (System.currentTimeMillis() - lastRebuild > REBUILD_COOLDOWN_MS) {
                release()
                queue += text
                init(app)
            }
        } else {
            EventLog.log(app, "VOICE", "spoke: " + text.take(40))
        }
    }

    /** The live engine, for callers that need to set pitch, rate or voice. */
    fun engine(): TextToSpeech? = if (ready) tts else null

    /**
     * Stops immediately, discarding anything queued.
     *
     * Needed because TextToSpeech finishes the current sentence no matter
     * what: when a call was answered, TOCO kept announcing the caller over the
     * top of the conversation until the sentence ran out.
     */
    fun stopSpeaking() {
        try {
            tts?.stop()
        } catch (e: Exception) {
            // Engine already gone.
        }
        queue.clear()
    }

    /** Replaces whatever is speaking instead of queueing behind it. */
    fun speakNow(context: Context, text: String) {
        if (text.isBlank()) return
        init(context)
        queue.clear()
        speakOrRebuild(text, TextToSpeech.QUEUE_FLUSH)
    }

    fun speak(context: Context, text: String) {
        if (text.isBlank()) return
        init(context)
        speakOrRebuild(text, TextToSpeech.QUEUE_ADD)
    }

    private fun say(text: String) {
        speakOrRebuild(text, TextToSpeech.QUEUE_ADD)
    }

    /** True when TOCO can actually be heard right now. */
    fun isHealthy(): Boolean = tts != null && ready

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
        release()
        queue.clear()
    }

    private const val UTTERANCE_ID = "toco"
    private const val REBUILD_COOLDOWN_MS = 3000L
}
