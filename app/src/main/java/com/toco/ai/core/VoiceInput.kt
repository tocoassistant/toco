package com.toco.ai.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * In-app voice input.
 *
 * Uses SpeechRecognizer directly rather than firing RecognizerIntent, so the
 * user never leaves TOCO and the orb can stay animated while it listens.
 *
 * One instance per screen. Call [destroy] in onDestroyView — a leaked
 * recognizer keeps the mic held open.
 */
class VoiceInput(private val context: Context) {

    interface Callback {
        fun onReady()
        fun onResult(text: String)
        fun onError(message: String)

        /**
         * Live microphone level, 0..1, several times a second while listening.
         * Lets the UI pulse with the voice. Default empty so existing callers
         * that don't care are unaffected.
         */
        fun onLevel(level: Float) {}
    }

    private var recognizer: SpeechRecognizer? = null
    private var listening = false

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start(callback: Callback) {
        if (listening) return

        if (!isAvailable()) {
            callback.onError("No speech recognition service on this device.")
            return
        }

        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            recognizer = it
        }

        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = callback.onReady()

            override fun onResults(results: Bundle?) {
                listening = false
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()

                if (text.isNullOrBlank()) {
                    callback.onError("Didn't catch that.")
                } else {
                    callback.onResult(text)
                }
            }

            override fun onError(error: Int) {
                listening = false
                callback.onError(describe(error))
            }

            override fun onBeginningOfSpeech() {}
            override fun onEndOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {
                // SpeechRecognizer reports RMS roughly in the range -2..10 dB.
                // Map that to 0..1 for the orb; clamp so noise spikes don't
                // slam it to full.
                val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
                callback.onLevel(normalized)
            }
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        listening = true
        r.startListening(intent)
    }

    fun stop() {
        listening = false
        recognizer?.stopListening()
    }

    fun destroy() {
        listening = false
        recognizer?.destroy()
        recognizer = null
    }

    /** Recognizer error codes are opaque ints; turn them into something readable. */
    private fun describe(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone error."
        SpeechRecognizer.ERROR_CLIENT -> "Recognition stopped."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied."
        SpeechRecognizer.ERROR_NETWORK -> "Network error."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Speech server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't hear anything."
        else -> "Voice input failed."
    }
}
