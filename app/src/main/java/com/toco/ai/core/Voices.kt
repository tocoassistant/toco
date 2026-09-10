package com.toco.ai.core

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * The voices TOCO can speak in.
 *
 * Honest note on how this works: Android does not ship a set of named
 * assistant voices an app can pick from. What exists is whatever the device's
 * TTS engine installed, which varies by phone. So each option here pairs a
 * real engine voice (when the device has more than one) with a pitch and speed
 * setting. That combination is what makes them sound distinct.
 *
 * If the device only exposes one voice, the options still differ — by pitch and
 * rate — and [availableVoiceCount] lets the UI say so plainly instead of
 * implying four separate voices are installed.
 */
object Voices {

    data class Option(
        val id: String,
        val label: String,
        val description: String,
        val pitch: Float,
        val speed: Float,
        /** Preferred engine voice index; falls back to the default if absent. */
        val voiceIndex: Int
    )

    val options = listOf(
        Option("default", "TOCO", "Neutral and even", 1.0f, 1.0f, 0),
        Option("calm", "Calm", "Lower and slower", 0.85f, 0.9f, 1),
        Option("bright", "Bright", "Higher and quicker", 1.2f, 1.1f, 2),
        Option("deep", "Deep", "Lowest, measured", 0.7f, 0.95f, 3)
    )

    fun byId(id: String): Option = options.firstOrNull { it.id == id } ?: options[0]

    /**
     * How many distinct engine voices this device actually has for English.
     * Used by the UI to be truthful about what varies between options.
     */
    fun availableVoiceCount(): Int = englishVoiceNames().size

    private fun englishVoiceNames(): List<String> {
        val engine = Voice.engine() ?: return emptyList()
        return try {
            engine.voices
                ?.filter { it.locale.language == Locale.ENGLISH.language }
                ?.map { it.name }
                ?.sorted()
                ?: emptyList()
        } catch (e: Exception) {
            // Some engines throw rather than returning null before init.
            emptyList()
        }
    }

    /** Applies [option] to the shared engine. Safe to call before TTS is ready. */
    fun apply(context: Context, option: Option) {
        Voice.init(context)
        val engine = Voice.engine() ?: return

        engine.setPitch(option.pitch)
        engine.setSpeechRate(option.speed)

        val names = englishVoiceNames()
        if (names.size > 1) {
            val name = names[option.voiceIndex % names.size]
            try {
                engine.voices?.firstOrNull { it.name == name }?.let { engine.voice = it }
            } catch (e: Exception) {
                // Engine refused the voice; pitch and rate still applied.
            }
        }
    }

    /** Re-applies the saved choice. Called at startup. */
    fun applySaved(context: Context) {
        apply(context, byId(Prefs(context).voiceId))
    }

    fun preview(context: Context, option: Option) {
        apply(context, option)
        Voice.speak(context, "This is the ${option.label} voice.")
    }
}
