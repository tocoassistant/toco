package com.toco.ai.service

import android.speech.RecognitionService

/**
 * Required by the voice-interaction config, which will not accept a missing
 * recognitionService component.
 *
 * TOCO does its own recognition inside WakeWordService using the platform
 * SpeechRecognizer, so this exists only to satisfy registration. Every method
 * ends the request immediately rather than pretending to recognise anything —
 * a stub that silently swallowed requests would break voice input in any app
 * that happened to bind to it.
 */
class TocoRecognitionService : RecognitionService() {

    override fun onStartListening(
        recognizerIntent: android.content.Intent?,
        listener: Callback?
    ) {
        // Not a real recognizer. Report failure so callers fall back.
        listener?.error(android.speech.SpeechRecognizer.ERROR_CLIENT)
    }

    override fun onCancel(listener: Callback?) {}

    override fun onStopListening(listener: Callback?) {}
}
